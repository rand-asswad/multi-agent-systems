/*
 *      _                    _     ____   ___  ____  
 *     / \   __ _  ___ _ __ | |_  | __ ) / _ \| __ ) 
 *    / _ \ / _` |/ _ \ '_ \| __| |  _ \| | | |  _ \ 
 *   / ___ \ (_| |  __/ | | | |_  | |_) | |_| | |_) |
 *  /_/   \_\__, |\___|_| |_|\__| |____/ \___/|____/ 
 *          |___/                                    
 *
 * Blue Agent "bob"
 * Flag-aware autonomous-decision agent
 */

/************************** Initial beliefs and rules **************************/

// dist/3: Manhattan distance D from agent's position to (X,Y)
dist(X, Y, D) :-
    myPos(MyX, MyY) &
    D = math.abs(MyX - X) + math.abs(MyY - Y).

// closestFlag/2: closest red flag position
closestFlag(X, Y) :-
    flag(red, X, Y) &
    dist(X, Y, D) &
    not (
        flag(red, X1, Y1) &
        dist(X1, Y1, D1) &
        D1 < D
    ).

/******************************** Initial goals ********************************/

!start.

/*********************************** Plans *************************************/

// when activated, enter world and attack
+!start <- enter; !attack.

// when dead, reenter world and attack
+dead <- -dead; enter; !attack.

// in attack mode find new target if target not defined
+!attack : not target(_,_) & closestFlag(X,Y) <- +target(X,Y); !!attack.

// in attack mode remove target when target is conquered
+!attack : target(X,Y) & flag(blue,X,Y) <- -target(X,Y); !!attack.

// move towards target
+!attack : target(X,_) & myPos(MyX,MyY) & X > MyX <- !right.
+!attack : target(X,_) & myPos(MyX,MyY) & X < MyX <- !left.
+!attack : target(_,Y) & myPos(MyX,MyY) & Y > MyY <- !down.
+!attack : target(_,Y) & myPos(MyX,MyY) & Y < MyY <- !up.

// move towards empty cell
+!left  : myPos(MyX,MyY) & pos(MyX-1, MyY, empty) <- left;  !!attack.
+!right : myPos(MyX,MyY) & pos(MyX+1, MyY, empty) <- right; !!attack.
+!up    : myPos(MyX,MyY) & pos(MyX, MyY-1, empty) <- up;    !!attack.
+!down  : myPos(MyX,MyY) & pos(MyX, MyY+1, empty) <- down;  !!attack.

// move around occupied cell
+!left  : myPos(MyX,MyY) & not pos(MyX-1, MyY, empty) & target(_,Y) & Y <= MyY <- !up.
+!left  : myPos(MyX,MyY) & not pos(MyX-1, MyY, empty) & target(_,Y) & Y >  MyY <- !down.
+!right : myPos(MyX,MyY) & not pos(MyX+1, MyY, empty) & target(_,Y) & Y <= MyY <- !up.
+!right : myPos(MyX,MyY) & not pos(MyX+1, MyY, empty) & target(_,Y) & Y >  MyY <- !down.
+!up    : myPos(MyX,MyY) & not pos(MyX, MyY-1, empty) & target(X,_) & X >= MyX <- !right.
+!up    : myPos(MyX,MyY) & not pos(MyX, MyY-1, empty) & target(X,_) & X <  MyX <- !left.
+!down  : myPos(MyX,MyY) & not pos(MyX, MyY+1, empty) & target(X,_) & X >= MyX <- !right.
+!down  : myPos(MyX,MyY) & not pos(MyX, MyY+1, empty) & target(X,_) & X >  MyX <- !left.
