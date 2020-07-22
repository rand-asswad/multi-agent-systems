import jason.asSyntax.*;
import jason.environment.*;
import java.util.logging.*;
import java.util.*;
import javax.swing.*;
import java.awt.*;
import javax.swing.border.*;

/**
 * Environnement d'un jeu de type Capture the flag
 *
 * @author Laurent Vercouter
 *
 */
public class CTFWorld extends TimeSteppedEnvironment {
    
    /** world model */
    public final static String TIMESTEP = "10";
    public final static boolean HAS_MAX_TIMESTEP = false;
    public final static int MAX_TIMESTEP = 1000;
    public final static int SIZE_X = 20;
    public final static int SIZE_Y = 20;
    public final static int NB_FLAGS = 4; // nb de drapeaux initial par camp
    public final static int NB_AGENTS = 5; // nb d'agent max par camp
    public final static int SCOPE = 2; // portee de perception
	public final static boolean PUBLIC_FLAG = false;

    public final static int EMPTY = 0;
    public final static int RED_FLAG = 1;
    public final static int RED_AGENT = 2;
    public final static int BLUE_FLAG = -1;
    public final static int BLUE_AGENT = -2;

    private int[][] worldModel = new int[SIZE_Y+2][SIZE_X+2];

    private Vector<Position> flags = new Vector<Position>();
    private int nbBlueFlags;
    private int nbRedFlags;
    private Hashtable<String,Position> blueAgents = new Hashtable<String,Position>();
    private Hashtable<String,Position> redAgents = new Hashtable<String,Position>();
    private Vector<String> dead = new Vector<String>();
    private int blueScore;
    private int redScore;
    
    private Random random = new Random();
    
    /** General delegations */
    private CTFGUI gui;
    private Logger   logger = Logger.getLogger("env."+CTFWorld.class.getName());

    public CTFWorld() {
		// use queue policy when an agent tries more than one action in the same cycle,
			// in queue policy, the second action is postponed for the next cycle.
			//setOverActionsPolicy(OverActionsPolicy.queue);
		gui = new CTFGUI();
		for (int i = 0; i < SIZE_Y+2; i++) {
			for (int j = 0; j < SIZE_X+2; j++) {
				worldModel[i][j] = CTFWorld.EMPTY;
			}
		}

		/* placement aleatoire des drapeaux */
		nbBlueFlags = CTFWorld.NB_FLAGS;
		nbRedFlags = CTFWorld.NB_FLAGS;
		for (int i = 0; i < CTFWorld.NB_FLAGS; i++) {
			int x = random.nextInt((SIZE_X/2)-6)+2;
			int y = random.nextInt(SIZE_Y-3)+2;
			while (!positionValide(x,y)) {
			x = random.nextInt((SIZE_X/2)-6)+2;
			y = random.nextInt(SIZE_Y-3)+2;
			}
			worldModel[y][x] = CTFWorld.BLUE_FLAG;
			flags.add(new Position(x,y));
		}
		for (int i = 0; i < CTFWorld.NB_FLAGS; i++) {
			int x = random.nextInt((SIZE_X/2)-5)+(SIZE_X/2)+3;
			int y = random.nextInt(SIZE_Y-3)+2;
			while (!positionValide(x,y)) {
			x = random.nextInt((SIZE_X/2)-5)+(SIZE_X/2)+3;
			y = random.nextInt(SIZE_Y-3)+2;
			}
			worldModel[y][x] = CTFWorld.RED_FLAG;
			flags.add(new Position(x,y));
		}
		gui.update(worldModel);
		blueScore = 0;
		redScore = 0;
    }

    @Override
    protected void stepStarted(int step) {
        blueScore = blueScore + nbBlueFlags;
        redScore = redScore + nbRedFlags;
        if ((CTFWorld.HAS_MAX_TIMESTEP) && (step > CTFWorld.MAX_TIMESTEP)) endGame();
        else logger.info("Pas no " + step);
    }
    
    /** Called before the MAS execution with the args informed in .mas2j */
    @Override
    public void init(String[] args) {
        super.init(new String[] { CTFWorld.TIMESTEP } ); // set step timeout
        setOverActionsPolicy(OverActionsPolicy.ignoreSecond);
    }

    /** donne une position aleatoire d'apparition d'un agent a cote d'un de ses drapeaux */
    private Position apparitionAleatoire(int couleurDrapeau) {
	Vector<Position> possibles = new Vector<Position>();
	for (int i = 0; i < flags.size(); i++) {
	    Position flagPos = flags.elementAt(i);
	    if (worldModel[flagPos.getY()][flagPos.getX()] == couleurDrapeau) {
		for (int j = flagPos.getY()-1; j <= flagPos.getY()+1; j++) {
		    for (int k = flagPos.getX()-1; k <= flagPos.getX()+1; k++) {
			if (((j != flagPos.getY())||(k != flagPos.getX())) && (worldModel[j][k] == CTFWorld.EMPTY))
			    possibles.add(new Position(k,j));
		    }
		}
	    }
	}
	if (possibles.isEmpty()) return null;
	else {
	    int val = random.nextInt(possibles.size());
	    return possibles.elementAt(val);
	}
    }

    private void tuer(String ag) {
        clearPercepts(ag);
        logger.info("l'agent " + ag + " decede...");
        dead.add(ag);
        addPercept(ag, Literal.parseLiteral("dead"));
    }
    
    /** Verifie que la position est valide pour un nouveau drapeau */
    private boolean positionValide(int x, int y) {
		for (int i = x-2; i <= x+2; i++) {
			for (int j = y-2; j <= y+2; j++) {
				if (worldModel[j][i] != CTFWorld.EMPTY) return false;
			}
		}
		return true;
    }
    
    /** Create the agents perceptions based on the world model */
    private void createPercept(String agent, Hashtable<String,Position> amis) {
        // remove previous perception
        clearPercepts(agent);
		// un agent mort ne percoit rien d'autre que sa propre condition
		if (dead.contains(agent)) {
			addPercept(agent, Literal.parseLiteral("dead"));
		} else {
			Position p;
			//il percoit la couleur de chaque agent
			Enumeration<String> names = blueAgents.keys();
			while (names.hasMoreElements()) {
				String ag = names.nextElement();
				addPercept(agent, Literal.parseLiteral("color(" + ag + ",blue)"));
			}
			names = redAgents.keys();
			while (names.hasMoreElements()) {
				String ag = names.nextElement();
				addPercept(agent, Literal.parseLiteral("color(" + ag + ",red)"));
			}
			if (CTFWorld.PUBLIC_FLAG) {
				// il perçoit la position des drapeaux
				for (int i = 0; i < flags.size(); i++) {
					p = flags.elementAt(i);
					if (worldModel[p.getY()][p.getX()] == CTFWorld.RED_FLAG)
						addPercept(agent, Literal.parseLiteral("flag(red," + p.getX() + ","+ p.getY() + ")"));
					else
						addPercept(agent, Literal.parseLiteral("flag(blue," + p.getX() + ","+ p.getY() + ")"));
				}
			}
			// il percoit sa propre position
			p = amis.get(agent);
			if (p != null) {
				addPercept(agent, Literal.parseLiteral("myPos(" + p.getX() + ","+ p.getY() + ")"));
			}
			// et les cases alentour
			int min_x = p.getX() - CTFWorld.SCOPE;
			if (min_x < 1) min_x = 1;
			int min_y = p.getY() - CTFWorld.SCOPE;
			if (min_y < 1) min_y = 1;
			int max_x = p.getX() + CTFWorld.SCOPE;
			if (max_x > CTFWorld.SIZE_X) max_x = CTFWorld.SIZE_X;
			int max_y = p.getY() + CTFWorld.SCOPE;
			if (max_y > CTFWorld.SIZE_Y) max_y = CTFWorld.SIZE_Y;
			for (int i = min_y; i <= max_y; i++) {
				for (int j = min_x; j <= max_x; j++) {
					if ((i != p.getY())||(j != p.getX()))
					switch(worldModel[i][j]) {
						case CTFWorld.RED_AGENT: addPercept(agent, Literal.parseLiteral("pos(" +j + ","+ i + ",redAgent)"));break;
						case CTFWorld.BLUE_AGENT: addPercept(agent, Literal.parseLiteral("pos(" +j + ","+ i + ",blueAgent)"));break;
						case CTFWorld.RED_FLAG: addPercept(agent, Literal.parseLiteral("pos(" +j + ","+ i + ",redFlag)"));break;
						case CTFWorld.BLUE_FLAG: addPercept(agent, Literal.parseLiteral("pos(" +j + ","+ i + ",blueFlag)"));break;
						case CTFWorld.EMPTY: addPercept(agent, Literal.parseLiteral("pos(" +j + ","+ i + ",empty)"));
					}
				}
			}
		}
    }

    private void createAllPercepts() {
        Enumeration<String> names = blueAgents.keys();
        while (names.hasMoreElements()) {
            String ag = names.nextElement();
            createPercept(ag,blueAgents);
        }
        names = redAgents.keys();
        while (names.hasMoreElements()) {
            String ag = names.nextElement();
            createPercept(ag,redAgents);
        }
    }
    
    @Override
    public boolean executeAction(String ag, Structure action) {
	Boolean retour = false;
        logger.info(ag + " doing "+action);
        try { Thread.sleep(500);}  catch (Exception e) {} // slow down the execution

	if (ag.startsWith("b")) {
	    retour = executeActionBleue(ag, action);
	} else if (ag.startsWith("r")) {
	    retour = executeActionRouge(ag, action);
	} else logger.info("Nom d'agent invalide : " + ag);
        gui.update(worldModel);
        if ((nbBlueFlags == 0) || (nbRedFlags == 0)) endGame();
        return retour;
    }
    
    private void endGame() {
        logger.info("Fin de la partie BLEU : " + blueScore + " ROUGE : " + redScore);
        gui.repaint();
        try { Thread.sleep(5000);}  catch (Exception e) {} // pause pour déguster la victoire
        stop();
    }

    private void conversionDrapeau(int x, int y, Hashtable<String,Position> ennemis) {
	worldModel[y][x] = -1 * worldModel[y][x];
	if (worldModel[y][x] == CTFWorld.BLUE_FLAG) {
	    nbBlueFlags++;
	    nbRedFlags--;
	} else {
	    nbBlueFlags--;
	    nbRedFlags++;
	}
	logger.info("le drapeau en  " + x + "," + y + " change de couleur");
	/* On tue tous les agents ennemis autour du drapeau */
	Enumeration<String> names = ennemis.keys();
	while (names.hasMoreElements()) {
	    String ag = names.nextElement();
	    Position pos = ennemis.get(ag);
	    if ((Math.abs(pos.getX()-x) <= 1) && (Math.abs(pos.getY()-y) <= 1)) {
			worldModel[pos.getY()][pos.getX()] = CTFWorld.EMPTY;
			ennemis.remove(ag);
			tuer(ag);
	    }
	}
    }

    private void voisinage(String ag, Hashtable<String,Position> amis, Hashtable<String,Position> ennemis, int drapeauEnnemi) {
		/* Conversion des drapeaux ennemis */
		Position pos = amis.get(ag);
		int x = pos.getX();
		int y = pos.getY();
		for (int i = y-1; i <= y+1; i++) {
			for (int j = x-1; j <= x+1; j++) {
				if (worldModel[i][j] == drapeauEnnemi)
					conversionDrapeau(j,i, ennemis);
			}
		}
		/* recherche d'ennemis voisins */
		Vector<String> ennemisVoisins = new Vector<String>();
		Enumeration<String> names = ennemis.keys();
		while (names.hasMoreElements()) {
			String voisin = names.nextElement();
			Position pos2 = ennemis.get(voisin);
			if ((Math.abs(pos2.getX()-x) <= 1) && (Math.abs(pos2.getY()-y) <= 1)) {
				ennemisVoisins.add(voisin);
			}
		}
		/* tirage aleatoire d'un ennemi a tuer dans les voisins */
		if (!ennemisVoisins.isEmpty()) {
			int v = random.nextInt(ennemisVoisins.size());
			String voisin = ennemisVoisins.elementAt(v);
			Position posVoisin = ennemis.get(voisin);
			worldModel[posVoisin.getY()][posVoisin.getX()] = CTFWorld.EMPTY;
			ennemis.remove(voisin);
			tuer(voisin);
			worldModel[y][x] = CTFWorld.EMPTY;
			amis.remove(ag);
			tuer(ag);
		}
    }

    /** execution d'une action parmi enter, left, right, up et down pour un agent du camp bleu */
    private boolean executeActionBleue(String ag, Structure action) {
	if (action.getFunctor().equals("enter")) {
	    logger.info("l'agent " + ag + " entre dans le systeme");
	    Position pos = apparitionAleatoire(CTFWorld.BLUE_FLAG);
	    if (blueAgents.size() >= CTFWorld.NB_AGENTS)
		logger.info("il y a trop d'agents bleus. " + ag + " n est pas place");
	    else if (pos == null)
		logger.info("Plus de place disponibles. " + ag + " n est pas place");
	    else if (blueAgents.containsKey(ag)){
            logger.info("l agent est deja entre");
        } else {
		blueAgents.put(ag,pos);
		worldModel[pos.getY()][pos.getX()] = CTFWorld.BLUE_AGENT;
		if (dead.contains(ag)) dead.remove(ag);
	    }
	} else {
	    Position pos = blueAgents.get(ag);
	    if (pos == null) {
		logger.info(ag + " ne bouge pas car il n a pas de position");
            logger.info("l'agent " + ag + " entre dans le systeme");
            pos = apparitionAleatoire(CTFWorld.BLUE_FLAG);
            if (blueAgents.size() >= CTFWorld.NB_AGENTS)
                logger.info("il y a trop d'agents bleus. " + ag + " n est pas place");
            else if (pos == null)
                logger.info("Plus de place disponibles. " + ag + " n est pas place");
            else if (blueAgents.containsKey(ag)){
                logger.info("l agent est deja entre");
            } else {
                blueAgents.put(ag,pos);
                worldModel[pos.getY()][pos.getX()] = CTFWorld.BLUE_AGENT;
                if (dead.contains(ag)) dead.remove(ag);
            }
	    } else {
			int x = pos.getX();
			int y = pos.getY();
			if (action.getFunctor().equals("left")) x = x-1;
			else if (action.getFunctor().equals("right")) x = x+1;
			else if (action.getFunctor().equals("up")) y = y-1;
			else if (action.getFunctor().equals("down")) y = y+1;
			else if (! action.getFunctor().equals("hold")) {
				logger.info("The action "+action+" is not implemented!");
				return false;
			}
			if ((x<1)||(y<1)||(x > CTFWorld.SIZE_X)||(y > CTFWorld.SIZE_Y))
				logger.info("Deplacement "+action+" impossible depuis " + pos);
			else if ((worldModel[y][x] != CTFWorld.EMPTY) && !((x == pos.getX()) && (y == pos.getY())))
				logger.info("Deplacement "+action+" impossible depuis " + pos + " car la case est occupee");
			else {
				worldModel[pos.getY()][pos.getX()] = CTFWorld.EMPTY;
				worldModel[y][x] = CTFWorld.BLUE_AGENT;
				blueAgents.put(ag,new Position(x,y));
				voisinage(ag, blueAgents, redAgents, CTFWorld.RED_FLAG);
			}
	    }
	}
        createAllPercepts();
	//createPercept(ag,blueAgents); // update agents perception for the new world state
        return true;
    }

    /** execution d'une action parmi enter, left, right, up et down pour un agent du camp rouge */
    private boolean executeActionRouge(String ag, Structure action) {
	if (action.getFunctor().equals("enter")) {
	    logger.info("l'agent " + ag + " entre dans le systeme");
	    Position pos = apparitionAleatoire(CTFWorld.RED_FLAG);
	    if (redAgents.size() >= CTFWorld.NB_AGENTS)
			logger.info("il y a trop d'agents rouges. " + ag + " n est pas place");
	    else if (pos == null)
			logger.info("Plus de place disponibles. " + ag + " n est pas place");
        else if (redAgents.containsKey(ag)){
            logger.info("l agent est deja entre");
        } else {
			redAgents.put(ag,pos);
			worldModel[pos.getY()][pos.getX()] = CTFWorld.RED_AGENT;
			if (dead.contains(ag)) dead.remove(ag);
	    }
	} else {
	    Position pos = redAgents.get(ag);
	    if (pos == null) {
		logger.info(ag + " ne bouge pas car il n a pas de position");
            logger.info("l'agent " + ag + " entre dans le systeme");
            pos = apparitionAleatoire(CTFWorld.RED_FLAG);
            if (redAgents.size() >= CTFWorld.NB_AGENTS)
                logger.info("il y a trop d'agents rouges. " + ag + " n est pas place");
            else if (pos == null)
                logger.info("Plus de place disponibles. " + ag + " n est pas place");
            else if (redAgents.containsKey(ag)){
                logger.info("l agent est deja entre");
            } else {
                redAgents.put(ag,pos);
                worldModel[pos.getY()][pos.getX()] = CTFWorld.RED_AGENT;
                if (dead.contains(ag)) dead.remove(ag);
            }
	    } else {
			int x = pos.getX();
			int y = pos.getY();
			if (action.getFunctor().equals("left")) x = x-1;
			else if (action.getFunctor().equals("right")) x = x+1;
			else if (action.getFunctor().equals("up")) y = y-1;
			else if (action.getFunctor().equals("down")) y = y+1;
			else if (! action.getFunctor().equals("hold")) {
				logger.info("The action "+action+" is not implemented!");
				return false;
			}
			if ((x<1)||(y<1)||(x > CTFWorld.SIZE_X)||(y > CTFWorld.SIZE_Y))
				logger.info("Deplacement "+action+" impossible depuis " + pos);
			else if (worldModel[y][x] != CTFWorld.EMPTY)
				logger.info("Deplacement "+action+" impossible depuis " + pos + " car la case est occupee");
			else {
				worldModel[pos.getY()][pos.getX()] = CTFWorld.EMPTY;
				worldModel[y][x] = CTFWorld.RED_AGENT;
				redAgents.put(ag,new Position(x,y));
				voisinage(ag, redAgents, blueAgents, CTFWorld.BLUE_FLAG);
			}
	    }
	}
        createAllPercepts();
        //createPercept(ag, redAgents); // update agents perception for the new world state
        return true;
    }
    
    @Override
    public void stop() {
        super.stop();
        gui.setVisible(false);
    }
    
    /* représente une position*/
    class Position {
		private int x, y;

		Position(int x, int y) {
			this.x = x;
			this.y = y;
		}

		public int getX() {
			return x;
		}

		public int getY() {
			return y;
		}

		@Override
		public String toString() {
			return "(x="+x+",y="+y+")";
		}
    }

    /* a simple GUI */
    class CTFGUI extends JFrame {

		private final static int WIDTH = 30;
		private final static int HEIGHT = 30;
	
        JLabel[][] labels = new JLabel[CTFWorld.SIZE_Y+2][CTFWorld.SIZE_X+2];

        CTFGUI() {
            super("CTF competition - GM5 2014-2015");
            getContentPane().setLayout(new GridLayout(CTFWorld.SIZE_Y+2,CTFWorld.SIZE_X+2));
			for (int j = 0; j < CTFWorld.SIZE_X+2; j++) {
				labels[0][j] = creeCase();
				labels[0][j].setBackground(Color.black);
				getContentPane().add(labels[0][j]);
			}
			for (int i = 1; i < CTFWorld.SIZE_Y+1; i++) {
				labels[i][0] = creeCase();
				labels[i][0].setBackground(Color.black);
				getContentPane().add(labels[i][0]);
				for (int j = 1; j < CTFWorld.SIZE_X+1; j++) {
					labels[i][j] = creeCase();
					getContentPane().add(labels[i][j]);
				}
				labels[i][CTFWorld.SIZE_X+1] = creeCase();
				labels[i][CTFWorld.SIZE_X+1].setBackground(Color.black);
				getContentPane().add(labels[i][CTFWorld.SIZE_X+1]);
			}
			for (int j = 0; j < CTFWorld.SIZE_X+2; j++) {
				labels[CTFWorld.SIZE_Y+1][j] = creeCase();
				labels[CTFWorld.SIZE_Y+1][j].setBackground(Color.black);
				getContentPane().add(labels[CTFWorld.SIZE_Y+1][j]);
			}
            pack();
            setVisible(true);
        }

		private JLabel creeCase() {
			JLabel lab = new JLabel();
			lab.setPreferredSize(new Dimension(CTFGUI.WIDTH,CTFGUI.HEIGHT));
			lab.setHorizontalAlignment(JLabel.CENTER);
			lab.setBorder(new EtchedBorder());
			return lab;
		}
		
		void update(int[][] cases) {
			for (int i = 0; i < CTFWorld.SIZE_Y+1; i++) {
				for (int j = 0; j < CTFWorld.SIZE_X+1; j++) {
					switch(cases[i][j]) {
						case CTFWorld.EMPTY:labels[i][j].setIcon(null);break;
						case CTFWorld.BLUE_FLAG:labels[i][j].setIcon(new ImageIcon("./images/blue_flag.png"));break;
						case CTFWorld.RED_FLAG:labels[i][j].setIcon(new ImageIcon("./images/red_flag.png"));break;
						case CTFWorld.BLUE_AGENT:labels[i][j].setIcon(new ImageIcon("./images/blue_agent.png"));break;
						case CTFWorld.RED_AGENT:labels[i][j].setIcon(new ImageIcon("./images/red_agent.png"));break;
					}
				}
			}
		}
    }
}
