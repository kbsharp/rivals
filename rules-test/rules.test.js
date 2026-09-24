// firestore.rules against the Firestore emulator: anyone can sign in, but a player only sees
// and changes their own profile, their rivalries and the sessions they played in.
//
// These run in Node rather than on the Android emulator, so they take seconds. The documents
// written here mirror what the app's repositories write (PlayerRepository, RivalryRepository);
// SyncTest in app/src/androidTest checks the app's own writes get through the rules.
//
// Run with `npm test` in this directory (it starts the Firestore emulator).
import { readFileSync } from 'node:fs';
import { after, before, beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { assertFails, assertSucceeds, initializeTestEnvironment } from '@firebase/rules-unit-testing';
import {
    collection, collectionGroup, deleteDoc, doc, getDoc, getDocs, query, serverTimestamp, setDoc, updateDoc,
    setLogLevel, where, writeBatch,
} from 'firebase/firestore';

const A = { uid: 'alice', email: 'iambevan@gmail.com', name: 'iambevan' };
const B = { uid: 'bob', email: 'julianjones56@gmail.com', name: 'julianjones56' };
const STRANGER = { uid: 'stranger', email: 'stranger@example.com', name: 'stranger' };

const pairId = (x, y) => [x, y].sort().join('_');

// Denials are the point here; don't log each one.
setLogLevel('silent');

let env;
before(async () => {
    env = await initializeTestEnvironment({
        projectId: 'demo-rivals',
        firestore: { rules: readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8') },
    });
});
after(() => env?.cleanup());

/** A signed-in client's Firestore, with the token claims Google sign-in gives. */
const as = (p, emailVerified = true) =>
    env.authenticatedContext(p.uid, { email: p.email, email_verified: emailVerified }).firestore();
const signedOut = () => env.unauthenticatedContext().firestore();

/** What PlayerRepository.upsert writes on first sign-in. */
function signUp(db, p) {
    return writeBatch(db)
        .set(doc(db, 'players', p.uid), { displayName: p.name, email: p.email, photoUrl: null, createdAt: serverTimestamp() })
        .set(doc(db, 'emails', p.email.toLowerCase()), { uid: p.uid })
        .commit();
}

/** RivalryRepository.invite: a pending rivalry. */
const invite = (db, me, rival) => setDoc(doc(db, 'rivalries', pairId(me.uid, rival.uid)), {
    playerIds: [me.uid, rival.uid].sort(), status: 'pending', invitedBy: me.uid, createdAt: serverTimestamp(),
});

/** RivalryRepository.accept. */
const accept = (db, id) => updateDoc(doc(db, 'rivalries', id), { status: 'active', acceptedAt: serverTimestamp() });

async function becomeRivals() {
    await invite(as(A), A, B);
    await accept(as(B), pairId(A.uid, B.uid));
    return pairId(A.uid, B.uid);
}

/** RivalryRepository.createInvite. */
const createInvite = (db, me, code) =>
    setDoc(doc(db, 'invites', code), { from: me.uid, fromName: me.name, createdAt: serverTimestamp() });

/** RivalryRepository.acceptInvite: the rivalry goes active and the invite is used up, in one batch. */
function acceptInvite(db, me, code, from) {
    return writeBatch(db)
        .set(doc(db, 'rivalries', pairId(me.uid, from)), {
            playerIds: [me.uid, from].sort(), status: 'active', invitedBy: from, inviteCode: code,
            createdAt: serverTimestamp(), acceptedAt: serverTimestamp(),
        })
        .delete(doc(db, 'invites', code))
        .commit();
}

const session = (me, rival, rivalryId = pairId(me, rival)) => ({
    playerIds: [me, rival], status: 'active', rivalryId, createdBy: me, matchWins: { [me]: 0, [rival]: 0 },
});

beforeEach(async () => {
    await env.clearFirestore();
    await signUp(as(A), A);
    await signUp(as(B), B);
    await signUp(as(STRANGER), STRANGER);
});

describe('finding players', () => {
    test('a player can be found by exact email but not listed', async () => {
        const db = as(STRANGER);
        const found = await assertSucceeds(getDoc(doc(db, 'emails', B.email)));
        assert.equal(found.data().uid, B.uid);
        await assertSucceeds(getDoc(doc(db, 'players', B.uid)));
        assert.equal((await getDoc(doc(db, 'emails', 'nobody@example.com'))).exists(), false);
        await assertFails(getDocs(collection(db, 'players')));
        await assertFails(getDocs(collection(db, 'emails')));
    });

    test("nobody can claim someone else's email or profile", async () => {
        const db = as(STRANGER);
        await assertFails(setDoc(doc(db, 'emails', A.email), { uid: STRANGER.uid }));
        await assertFails(setDoc(doc(db, 'players', A.uid), { displayName: 'Not A' }));
    });

    test("an unverified email can't be indexed", async () => {
        const p = { uid: 'unverified', email: 'unverified@example.com', name: 'unverified' };
        await assertFails(signUp(as(p, false), p));
    });
});

describe('rivalries', () => {
    test('a session needs an accepted rivalry', async () => {
        const id = pairId(A.uid, B.uid);
        await assertSucceeds(invite(as(A), A, B));
        await assertFails(setDoc(doc(as(A), 'sessions', 's'), session(A.uid, B.uid)));

        // The inviter can't accept their own invite; the invited player can.
        await assertFails(accept(as(A), id));
        await assertSucceeds(accept(as(B), id));
        await assertSucceeds(setDoc(doc(as(A), 'sessions', 's'), session(A.uid, B.uid)));
    });

    test("strangers can't touch a rivalry or its sessions", async () => {
        const rivalryId = await becomeRivals();
        const a = as(A);
        await setDoc(doc(a, 'sessions', 's'), session(A.uid, B.uid));
        await setDoc(doc(a, 'sessions/s/matches/m'), { playerIds: [A.uid, B.uid], number: 1 });
        await setDoc(doc(a, 'sessions/s/matches/m/frames/f'), { playerIds: [A.uid, B.uid], winnerId: A.uid });
        await assertSucceeds(getDoc(doc(as(B), 'sessions/s/matches/m/frames/f')));

        const db = as(STRANGER);
        await assertFails(getDoc(doc(db, 'rivalries', rivalryId)));
        await assertFails(deleteDoc(doc(db, 'rivalries', rivalryId)));
        await assertFails(getDoc(doc(db, 'sessions', 's')));
        await assertFails(getDocs(collection(db, 'sessions/s/matches')));
        await assertFails(getDocs(collection(db, 'sessions')));
        await assertFails(getDocs(collectionGroup(db, 'frames')));
        const mine = await assertSucceeds(getDocs(query(collectionGroup(db, 'frames'), where('playerIds', 'array-contains', STRANGER.uid))));
        assert.equal(mine.size, 0);
        await assertFails(updateDoc(doc(db, 'sessions', 's'), { status: 'ended' }));
    });

    test("a stranger can't start a session in someone else's rivalry or rig the players", async () => {
        const rivalryId = await becomeRivals();
        await assertFails(setDoc(doc(as(STRANGER), 'sessions', 's'), session(STRANGER.uid, B.uid, rivalryId)));
        // A member can't swap a stranger into their rivalry's session either.
        await assertFails(setDoc(doc(as(A), 'sessions', 's'), session(A.uid, STRANGER.uid, rivalryId)));
        // Nor invent a rivalry nobody accepted.
        await assertFails(setDoc(doc(as(STRANGER), 'rivalries', pairId(STRANGER.uid, A.uid)), {
            playerIds: [STRANGER.uid, A.uid].sort(), status: 'active', invitedBy: A.uid,
        }));
    });

    test('a declined invite can be deleted by the invited player', async () => {
        const id = pairId(A.uid, B.uid);
        await invite(as(A), A, B);
        await assertFails(deleteDoc(doc(as(STRANGER), 'rivalries', id)));
        await assertSucceeds(deleteDoc(doc(as(B), 'rivalries', id)));
        // A missing rivalry can still be looked up by either would-be player, so A sees it go.
        assert.equal((await assertSucceeds(getDoc(doc(as(A), 'rivalries', id)))).exists(), false);
    });
});

describe('invite links', () => {
    test('an invite can be read signed out but only used once', async () => {
        await assertSucceeds(createInvite(as(A), A, 'CODE2345'));
        const read = await assertSucceeds(getDoc(doc(signedOut(), 'invites', 'CODE2345')));
        assert.equal(read.data().fromName, 'iambevan');

        await assertSucceeds(acceptInvite(as(B), B, 'CODE2345', A.uid));
        assert.equal((await getDoc(doc(as(STRANGER), 'invites', 'CODE2345'))).exists(), false);
        // A made-up invite gets nobody anywhere.
        await assertFails(acceptInvite(as(STRANGER), STRANGER, 'FAKECODE', A.uid));
    });

    test("an invite can't be forged for someone else", async () => {
        await assertFails(setDoc(doc(as(STRANGER), 'invites', 'ABCDEFGH'), {
            from: A.uid, fromName: 'A', createdAt: serverTimestamp(),
        }));
    });

    test("a used invite can't be replayed by a stranger", async () => {
        await createInvite(as(A), A, 'CODE2345');
        await acceptInvite(as(B), B, 'CODE2345', A.uid);
        await assertFails(acceptInvite(as(STRANGER), STRANGER, 'CODE2345', A.uid));
    });
});

test('signed out sees nothing private', async () => {
    const db = signedOut();
    await assertFails(getDoc(doc(db, 'players', A.uid)));
    await assertFails(getDocs(query(collection(db, 'sessions'), where('playerIds', 'array-contains', A.uid))));
});
