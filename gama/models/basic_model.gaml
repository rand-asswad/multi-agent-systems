/**
*  _                                       _____ _       
* | |   _   _ _ __   ___ _ __ __ _ _   _  |  ___| |_   _ 
* | |  | | | | '_ \ / _ \ '__/ _` | | | | | |_  | | | | |
* | |__| |_| | | | |  __/ | | (_| | |_| | |  _| | | |_| |
* |_____\__,_|_| |_|\___|_|  \__,_|\__, | |_|   |_|\__,_|
*                                  |___/                 
*/

model luneray_flu

global {
	// model parameters
	int nb_people <- 2248;				// total number of people
	int nb_infected_init <- 5;			// initial number of infected people (default: 5)
	int nb_immune_init <- 250;			// initial number of immune people (default: 0)
	int nb_doctor <- 10;				// number of doctors (default: 5)
	float step <- 1 #mn;				// model time step
	float total_time <- 14 #h;			// total model duration
	
	// people/doctors parameters
	int max_medicine <- 300;			// number of medication a doctor can hold (default: 20)
	float proba_people_move <- 0.01;	// probability of people leaving a building
	float proba_infection <- 0.05;		// probability of infecting people nearby
	float dist_infection <- 10 #m;		// infection distance
	float dist_examine <- 15 #m;		// distance to examine a patient
	float dist_heal <- 5 #m;			// distance to heal a patient

	// model geometry
	file roads_shapefile <- file("../includes/routes.shp");
	file buildings_shapefile <- file("../includes/batiments.shp");
	geometry shape <- envelope(roads_shapefile);
	graph road_network;

	// count and update infections
	int nb_people_immune <- nb_immune_init update: people count (each.is_immune);
	int nb_people_infected <- nb_infected_init update: people count (each.is_infected);
	int nb_people_susceptible <- nb_people - nb_infected_init - nb_immune_init update: nb_people - (nb_people_immune + nb_people_infected);
	float infected_rate update: nb_people_infected/nb_people;
	float immune_rate update: nb_people_immune/nb_people;
	
	init {
		create road from: roads_shapefile;
		road_network <- as_edge_graph(road);
		create building from: buildings_shapefile with:[type::string(read ("NATURE"))]{
			if type="pharmacy" {
				color <- #darkgreen ;
				isPharmacy <- true;
			}
		}
		create people number:nb_people {
			my_house <- one_of(building where (!each.isPharmacy));
			location <- any_location_in(my_house);
		}
		ask nb_immune_init among people {
			is_immune <- true;
		}
		ask nb_infected_init among people where !each.is_immune {
			is_infected <- true;
		}
		create doctor number:nb_doctor {
			my_pharmacy <- first(building where (each.isPharmacy));
			location <- any_location_in(my_pharmacy);
		}
	}
	
	reflex end_simulation when: nb_people_infected = 0 or time >= total_time {
		do pause;
		write nb_people_infected;
	}

	// predicates for doctor BDI
	predicate find_sick <- new_predicate("find sick");
	predicate has_medicine <- new_predicate("has medicine", true);
	predicate no_medicine <- new_predicate("has medicine", false);
}

species people skills:[moving] {		
	float speed <- (2 + rnd(3)) #km/#h;
	bool is_infected <- false;
	bool is_immune <- false;
	building my_house;
	point target;
	
	// choose a target (if there isn't one)
	reflex stay when: target = nil {
		if flip(proba_people_move) {
			// if agent at home, go out randomly
			if (overlaps(location, my_house)) {
				target <- any_location_in (one_of(building));
			} else {
				// go back home
				target <- any_location_in(my_house);
			}
		}
	}
	
	// move towards target (if defined)
	reflex move when: target != nil {
		do goto target:target on: road_network;
		// arrive to target
		if (location = target) {
			target <- nil;
		} 
	}
	
	// contaminate people around
	reflex infect when: is_infected {
		ask people at_distance dist_infection {
			if flip(proba_infection) and !is_immune {
				is_infected <- true;
			}
		}
	}

	aspect shape {
		if (is_immune) {
			draw triangle(15) color: #blue;
		} else {
			draw circle(5) color:is_infected ? #red : #green;
		}
	}
}

species doctor skills:[moving] control:simple_bdi {
	float speed <- (6 + rnd(2)) #km/#h;
	int nb_medicine <- max_medicine;
	building my_pharmacy;

	init {
		do add_desire(find_sick);
	}

	// Do I have medicine ?
	perceive target:self {
		if (nb_medicine > 0) {
			do add_belief(has_medicine);
			do remove_belief(no_medicine);
		} else {
			do add_belief(no_medicine);
			do remove_belief(has_medicine);
		}
	}

	// perceive sick people around
	perceive target:people where each.is_infected in:dist_examine {
		focus id:"sick_agent" var:name;
		ask myself {
			do remove_desire(find_sick);
		}
	}

	rule belief: new_predicate("sick_agent") new_desire: get_predicate(get_belief_with_name("sick_agent"));
	rule belief: no_medicine new_desire: has_medicine;

	// when the intention is to find sick people, just wander around
	plan wander_around intention: find_sick {
		do wander on: road_network;
	}
	
	// when the intention is to have medicine, restock at the pharmacy
	plan restock intention: has_medicine {
		if (overlaps(location, my_pharmacy)) {
			nb_medicine <- max_medicine;
		} else {
			do goto target:my_pharmacy.location on:road_network;
		}
	}

	// give medicine to known sick person
	plan give_medicine intention: new_predicate("sick_agent") {
		// get sick person by name
		string sick_target <- get_predicate(get_current_intention()).values["name_value"];
		people sick_person <- people first_with (each.name = sick_target);

		if (nb_medicine > 0) {
			if (self distance_to sick_person.location <= dist_heal) {
				if (sick_person.is_infected) {
					nb_medicine <- nb_medicine - 1;
					sick_person.is_infected <- false;
					sick_person.is_immune <- true;
				} else {
					do remove_belief(get_predicate(get_current_intention()));
					do remove_intention(get_predicate(get_current_intention()), true);
					do add_desire(find_sick);
				}
			} else {
				do goto target: sick_person.location on: road_network;
			}
		} else {
			do add_subintention(get_current_intention(), has_medicine, true);
			do current_intention_on_hold();
		}
	}

	aspect cross {
		draw cross(10,3) color: #magenta;
	}
}

species road {
	aspect geom {
		draw shape color: #black;
	}
}

species building {
	rgb color <- #gray;
	string type;
	bool isPharmacy <- false;
	
	aspect geom {
		draw shape color: color;
	}
}


experiment main_experiment type:gui {
	parameter "Number of infected people initially:" var:nb_infected_init min:1 max:1500;
	parameter "Number of immune people initially:" var:nb_immune_init min:1 max:1500;
	parameter "Number of doctors:" var:nb_doctor min:0 max:200;
	parameter "Maximum number of medication per doctor:" var:max_medicine min:0 max:1000;
	
	parameter "Probability of moving" var:proba_people_move min:0.0 max:1.0;
	parameter "Probability of infection" var:proba_infection min:0.0 max:1.0;
	parameter "Distance of infection" var:dist_infection min:0.0 max:50.0 #m;
	parameter "Distance for examining patients" var:dist_examine min:0.0 max:50.0 #m;
	parameter "Distance for healing patients" var:dist_heal min:0.0 max:50.0 #m;

	output {
		display plot refresh: every(15 #cycle) {
			chart "Influenza Propagation in Luneray" type: series {
				data "susceptible" value: nb_people_susceptible color:#green;
				data "infected" value: nb_people_infected color:#red;
				data "immune" value: nb_people_immune color:#blue;
			}
		}
		
		display map type: opengl{
			species road aspect:geom;
			species building aspect:geom;
			species people aspect:shape;		
			species doctor aspect:cross;	
		}
		
	}
}
