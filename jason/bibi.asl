/*
 *      _                    _     ____ ___ ____ ___ 
 *     / \   __ _  ___ _ __ | |_  | __ )_ _| __ )_ _|
 *    / _ \ / _` |/ _ \ '_ \| __| |  _ \| ||  _ \| | 
 *   / ___ \ (_| |  __/ | | | |_  | |_) | || |_) | | 
 *  /_/   \_\__, |\___|_| |_|\__| |____/___|____/___|
 *          |___/                                    
 *
 * Blue Blind Agent "bibi"
 * Flag-unaware cooparative agent
 */

/************************** Initial beliefs and rules **************************/

// isAdjacent/2: Agent is adjacent to (X,Y)
isAdjacent(X,Y) :- myPos(X-1, Y).
isAdjacent(X,Y) :- myPos(X+1, Y).
isAdjacent(X,Y) :- myPos(X, Y-1).
isAdjacent(X,Y) :- myPos(X, Y+1).
isAdjacent(X,Y) :- myPos(X-1, Y-1).
isAdjacent(X,Y) :- myPos(X+1, Y-1).
isAdjacent(X,Y) :- myPos(X+1, Y+1).
isAdjacent(X,Y) :- myPos(X-1, Y+1).

// randomTarget/2: assigns random target in enemy camp
randomTarget(X, Y) :-
    X = 11 + math.floor(math.random( 9)) &
    Y =  1 + math.floor(math.random(19)).

// waiting/2: all teammates are gathered waiting by the flag at (X,Y)
allWaiting(X,Y) :- waiting(X,Y)[source(self)] & waiting(X,Y)[source(bibi2)] & waiting(X,Y)[source(bibi3)].
allWaiting(X,Y) :- waiting(X,Y)[source(self)] & waiting(X,Y)[source(bibi1)] & waiting(X,Y)[source(bibi3)].
allWaiting(X,Y) :- waiting(X,Y)[source(self)] & waiting(X,Y)[source(bibi1)] & waiting(X,Y)[source(bibi2)].

/******************************** Initial goals ********************************/

!start.

/*********************************** Plans *************************************/

// when activated, enter world and go
// Replaced "attack" with "go" since we don't know where we are going
+!start <- enter; !go.

// when dead, reenter world and go
+dead <- -dead; enter; !go.

// clear mission variable and move on to next flag
+done(X,Y)[source(_)] : flagTarget(X,Y) & place(MyX,MyY) <-
    .print("Moving on to next flag!");
    .send([bibi1, bibi2, bibi3], untell, flagTarget(X,Y));
    .send([bibi1, bibi2, bibi3], untell, waiting(X,Y));
    .send([bibi1, bibi2, bibi3], untell, place(MyX,MyY));
    -flagTarget(_,_);
    -waiting(_,_)[source(_)];
    -place(_,_)[source(_)];
    -target(_,_);
    !!go.

// we're all waiting
+!wait : allWaiting(X,Y) <-
    .print("Flag at (", X, ",", Y, ") has been successfully conquered");
    .send([bibi1, bibi2, bibi3], tell, done(X,Y)).
// else: keep waiting
+!wait <- hold; !wait.

// arrived at flag destination -> inform teammates
+!go
    :   flagTarget(X,Y) &                   // the flag I'm attacking
        not waiting(X,Y)[source(self)] &    // I'm not waiting yet
        isAdjacent(X,Y) &                   // I'm near the flag
        myPos(MyX,MyY)                      // my position
    <-  .print("Waiting at at (", X,",",Y,")");
        .send([bibi1, bibi2, bibi3], tell, waiting(X,Y));       // tell them I'm waiting
        .send([bibi1, bibi2, bibi3], tell, place(MyX,MyY));     // tell them my place
        !wait.  // now we wait for others to arrive

// rearrange agents around flag (to avoid clashing with each other)
+!go // claim place on upper tile
    :   not waiting(_,_)[source(self)] &        // having found a tile to wait on
        flagTarget(X,Y) &                       // the flag tile I want to wait by
        pos(X,Y-1,empty) &                      // upper tile is empty
        not place(X,Y-1)[source(Sender)] &      // upper tile haven't been claimed by someone
        not Sender == self                      // that someone isn't me
    <-  .send([bibi1, bibi2, bibi3], tell, place(X,Y-1));   // I claim my place
        -target(_,_);       // I clear my target
        +target(X,Y-1);     // I set my target to my place
        !go.                // I go to my place
+!go // claim place on right tile
    :   not waiting(_,_)[source(self)] &
        flagTarget(X,Y) &
        pos(X+1,Y,empty) &
        not place(X+1,Y)[source(Sender)] &
        not Sender == self
    <-  .send([bibi1, bibi2, bibi3], tell, place(X+1,Y));
        -target(_,_);
        +target(X+1,Y);
        !go.
+!go // claim place on lower tile
    :   not waiting(_,_)[source(self)] &
        flagTarget(X,Y) &
        pos(X,Y+1,empty) &
        not place(X,Y+1)[source(Sender)] &
        not Sender == self
    <-  .send([bibi1, bibi2, bibi3], tell, place(X,Y+1));
        -target(_,_);
        +target(X,Y+1);
        !go.
+!go // claim place on left tile
    :   not waiting(_,_)[source(self)] &
        flagTarget(X,Y) &
        pos(X-1,Y,empty) &
        not place(X-1,Y)[source(Sender)] &
        not Sender == self
    <-  .send([bibi1, bibi2, bibi3], tell, place(X-1,Y));
        -target(_,_);
        +target(X-1,Y);
        !go.

// receive message about newfound flag (from another agent)
+flagTarget(X,Y)[source(Sender)] : not (Sender == self) <-
    .print("I heard from ", Sender," there's a flag at: (",X,",",Y,")");
    -target(_, _);-target(_,_); // remove old target
    +target(X, Y);      // add new target
    !!go.               // go to new flag

// detect red flag target and tell teammates
+!go
    :   not flagTarget(_,_) &   // WE are not chasing a flag (we're available to new targets)
        myPos(MyX,MyY) &        // my position
        pos(X,Y,redFlag) &      // the position of the new red flag
        not done(X,Y)           // the flag hasn't been conquered yet
    <-  .send([bibi1, bibi2, bibi3], tell, flagTarget(X,Y));    // inform teammates
        .print("Spotted red flag at (", X,",",Y,")");
        -target(_, _);      // remove old target
        +target(X, Y);      // add new target
        +flagTarget(X,Y);   // add new flag target
        !!go.               // go to flag (closer)


// find random target if target is not defined
+!go
    :   not target(_,_) &       // no target defined
        not flagTarget(_,_) &   // not chasing a flag
        randomTarget(X, Y)      // a random target (X,Y)
    <-  .print("Set new random target: (",X,",",Y,")");
        +target(X, Y);          // add new random target
        !!go.                   // go to target
        

// reached old target -> remove target
+!go : target(X,Y) & isAdjacent(X,Y) & not flagTarget(_,_) <- -target(X,Y); !!go.

// move towards target
+!go : target(X,Y) & myPos(MyX,MyY) & X > MyX <- !right.
+!go : target(X,Y) & myPos(MyX,MyY) & Y > MyY <- !down.
+!go : target(X,Y) & myPos(MyX,MyY) & Y < MyY <- !up.
+!go : target(X,Y) & myPos(MyX,MyY) & X < MyX <- !left.
+!go <- hold; !!go. // else

// move towards empty cell
+!left  : myPos(MyX,MyY) & pos(MyX-1, MyY, empty) <- left;  !go.
+!right : myPos(MyX,MyY) & pos(MyX+1, MyY, empty) <- right; !go.
+!up    : myPos(MyX,MyY) & pos(MyX, MyY-1, empty) <- up;    !go.
+!down  : myPos(MyX,MyY) & pos(MyX, MyY+1, empty) <- down;  !go.

// move around occupied cell
+!left  : myPos(MyX,MyY) & not pos(MyX-1, MyY, empty) <- !down.
+!right : myPos(MyX,MyY) & not pos(MyX+1, MyY, empty) <- !down.
+!up    : myPos(MyX,MyY) & not pos(MyX, MyY-1, empty) <- !left.
+!down  : myPos(MyX,MyY) & not pos(MyX, MyY+1, empty) <- !left.
