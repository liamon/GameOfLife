package game;

import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;
import javax.swing.*;

import java.io.*;
import java.util.Random;

@SuppressWarnings("serial") // Added this to stop Eclipse/compiler complaining.
public class GameOfLife extends JFrame implements Runnable {
	private static final int GAME_AREA_LENGTH = 800;
	
	// These two values found by trial and error.
	// They may not be correct on OSes besides Windows 10.
	private static final int BORDER_SIZE_OFFSET = 8;
	private static final int TITLE_BAR_OFFSET = 31;
	
	// Before uploading to GitHub, I factored out some "magic numbers"
	// into the following constants:
	private static final int BUTTON_WIDTH = 64;
	private static final int BUTTON_HEIGHT = 22;
	private static final int BUTTON_X_ORIGIN = 8;
	private static final int START_Y_ORIGIN = 9;
	private static final int RANDOM_Y_ORIGIN = 49;
	private static final int SAVE_Y_ORIGIN = 89;
	private static final int LOAD_Y_ORIGIN = 129;
	
	// Handily, the default value for booleans in Java is false.
	private boolean[][] liveCellsOnScreen = new boolean[40][40];
	private boolean[][] cellBuffer = new boolean[40][40];
	private BufferStrategy strategy;
	
	private boolean isActive = false;
	private boolean isReadyToPaint = false;
	
	private Point mousePressPosition;
	private int[] newlyChangedCell = new int[2];
	private String saveFileName = "savedGame.txt";

	public GameOfLife() {
		Dimension monitorSize = java.awt.Toolkit.getDefaultToolkit().getScreenSize();
		int centeredXOrigin = monitorSize.width / 2 - GAME_AREA_LENGTH / 2;
		int centeredYOrigin = monitorSize.height / 2 - GAME_AREA_LENGTH / 2;

		setTitle("Conway's Game of Life");
		setDefaultCloseOperation(EXIT_ON_CLOSE);
		setBounds(centeredXOrigin, centeredYOrigin,
				BORDER_SIZE_OFFSET + GAME_AREA_LENGTH + BORDER_SIZE_OFFSET,
				TITLE_BAR_OFFSET + GAME_AREA_LENGTH + BORDER_SIZE_OFFSET);
		setVisible(true);
		
		createBufferStrategy(2);
		strategy = getBufferStrategy();
		
		// Since uploading to GitHub, I have changed this class from implementing MouseListener
		// and MouseMotionListener to anonymous inner classes to passing these two methods.
		addMouseListener(new MouseAdapter() {
			@Override
			public void mousePressed(MouseEvent e) {
				// This needs to be recorded so that it works when you don't
				// actually drag the mouse around but just press and release it.
				mousePressPosition = e.getPoint();
			}
			
			@Override
			public void mouseReleased(MouseEvent e) {
				// Use of mouseReleased means that if you accidentally click on the wrong
				// place, you will be able to move the cursor to the right place and let go.
				if (e.getButton() == MouseEvent.BUTTON1 && !isActive) {
					Point mousePosition = e.getPoint();
					if (isStartClicked(mousePosition)) {
						isActive = true;
						return; // Otherwise will change the state of the cell the cursor is over.
					}
					if (isRandomClicked(mousePosition)) {
						randomGrid();
						return;
					}
					if (isSaveClicked(mousePosition)) {
						saveGame();
						return;
					}
					if (isLoadClicked(mousePosition)) {
						loadGame();
						return;
					}
					// i.e. if there is no net movement of the cursor
					if (mousePosition.equals(mousePressPosition)) { // == doesn't work as they are objects.
						// Must account for offsets by subtracting them.
						// i.e. if there was no movement of the cursor
						int horizontal = (mousePosition.x - BORDER_SIZE_OFFSET) / 20;
						int vertical = (mousePosition.y - TITLE_BAR_OFFSET) / 20;
						liveCellsOnScreen[vertical][horizontal] = !liveCellsOnScreen[vertical][horizontal];
					}
				}
				// else do nothing, only change status of cell if left button is clicked.
			}
		});
		
		addMouseMotionListener(new MouseMotionAdapter() {
			@Override
			public void mouseDragged(MouseEvent e) {
				// Note that I have taken out e.getButton() == MouseEvent.BUTTON1 as
				// this is only true when the button changes state (pressed/released).
				if (isActive) {
					return;
				}
				Point mousePos = e.getPoint();
				
				// Must account for offsets by subtracting them.
				int horizontal = (mousePos.x - BORDER_SIZE_OFFSET) / 20;
				int vertical = (mousePos.y - TITLE_BAR_OFFSET) / 20;

				// This if statement prevents cells from rapidly switching from live to dead.
				// If the || below was a && instead, you would not be able to draw straight lines.
				if (newlyChangedCell[0] != vertical || newlyChangedCell[1] != horizontal) {
					liveCellsOnScreen[vertical][horizontal] = !liveCellsOnScreen[vertical][horizontal];
					newlyChangedCell[0] = vertical;
					newlyChangedCell[1] = horizontal;
				}
			}
		});
		
		Thread t = new Thread(this);
		t.start();
		isReadyToPaint = true;
	}

	@Override
	public void run() {
		while (true) {
			try {
				// A smaller frame rate so it is easier to follow what happens.
				Thread.sleep(125); // 8 frames per second.
			} catch (InterruptedException ex) { }
			
			repaint();
		}
	}
	
	@Override
	public void paint(Graphics g) {
		if (!isReadyToPaint) {
			return;
		}
		g = strategy.getDrawGraphics();
		
		// I originally individually painted each cell whether it is dead or alive. However,
		// it might be more efficient to start off by painting a field of all dead cells and
		// then only specifically painting an individual cell if it is alive.
		g.setColor(Color.WHITE);
		g.fillRect(BORDER_SIZE_OFFSET, TITLE_BAR_OFFSET, GAME_AREA_LENGTH, GAME_AREA_LENGTH);
		
		if (isActive) {
			newTurn();
		}
		
		for (int yIndex = 0; yIndex < 40; yIndex++) {
			for (int xIndex = 0; xIndex < 40; xIndex++) {
				int x = BORDER_SIZE_OFFSET + xIndex * 20;
				int y = TITLE_BAR_OFFSET + yIndex * 20;
				
				// Cells are black if live and white if dead.
				if (liveCellsOnScreen[yIndex][xIndex]) {
					g.setColor(Color.BLACK);
					g.fillRect(x, y, GAME_AREA_LENGTH / 40, GAME_AREA_LENGTH / 40);
				}
			}
		}
		// At first, I had this before the nested for loops, but that meant that
		// the cells were being painted after the buttons and covering them up.
		if (!isActive) {
			paintButtons(g);
		}
		
		g.dispose();
		strategy.show();
	}
	
	private void newTurn() {
		int liveNeighbours;
		for (int y = 0; y < 40; y++) {
			for (int x = 0; x < 40; x++) {
				liveNeighbours = 0;
				
				for (int yBeside = -1; yBeside <= 1; yBeside++) {
					for (int xBeside = -1; xBeside <= 1; xBeside++) {
						// This if statement stops cells counting themselves as neighbours.
						if (!(yBeside == 0 && xBeside == 0)) {
							if (liveNeighbourInPosition(y, yBeside, x, xBeside)) {
								liveNeighbours++;
							}
						}
					}
				}
				
				// Boolean values are all initialized to false so I only need to explicitly
				// deal with the cases where a cell will become alive in the next turn.
				if (!liveCellsOnScreen[y][x] && liveNeighbours == 3) {
					cellBuffer[y][x] = true;
				} else if (liveCellsOnScreen[y][x] &&
						(liveNeighbours == 2 || liveNeighbours == 3)) {
					cellBuffer[y][x] = true;
				} else {
					cellBuffer[y][x] = false;
				}
			}
		}
		for (int y = 0; y < 40; y++) {
			for (int x = 0; x < 40; x++) {
				liveCellsOnScreen[y][x] = cellBuffer[y][x];
			}
		}
	}
	
	// Before uploading this to GitHub, I noticed an unnecessary if-statement here,
	// which I refactored out.
	private boolean liveNeighbourInPosition(int y, int yBeside, int x, int xBeside) {
		return liveCellsOnScreen[positiveMod(y + yBeside, 40)][positiveMod(x + xBeside, 40)];
	}
	
	private int positiveMod(int dividend, int modulus) {
		// Based off a solution described in this link: http://stackoverflow.com/a/4412200
		// This is needed because Java before Java 8 did not have Math.floorMod.
		return (dividend % modulus + modulus) % modulus;
	}

	private void paintButtons(Graphics g) {
		paintButtonBackground(g);
		g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 16));
		
		int startTextOffset = 8 + 64 / 2 - g.getFontMetrics().stringWidth("Start") / 2;
		int randomTextOffset = 8 + 64 / 2 - g.getFontMetrics().stringWidth("Random") / 2;
		int saveTextOffset = 8 + 64 / 2 - g.getFontMetrics().stringWidth("Save") / 2;
		int loadTextOffset = 8 + 64 / 2 - g.getFontMetrics().stringWidth("Load") / 2;
		int fontHeight = g.getFontMetrics().getHeight();
		
		g.setColor(Color.BLACK);
		// The y of the string is the baseline, so must add the dimensions
		// of the string (the height in this case) instead of subtracting it.
		g.drawString("Start", BORDER_SIZE_OFFSET + startTextOffset,
				TITLE_BAR_OFFSET + 9 + 22 / 2 + fontHeight / 2);
		g.drawString("Random", BORDER_SIZE_OFFSET + randomTextOffset,
				TITLE_BAR_OFFSET + 49 + 22 / 2 + fontHeight / 2);
		g.drawString("Save", BORDER_SIZE_OFFSET + saveTextOffset,
				TITLE_BAR_OFFSET + 89 + 22 / 2 + fontHeight / 2);
		g.drawString("Load", BORDER_SIZE_OFFSET + loadTextOffset,
				TITLE_BAR_OFFSET + 129 + 22 / 2 + fontHeight / 2);
	}
	
	private void paintButtonBackground(Graphics g) {
		g.setColor(new Color(211, 211, 211)); // Light grey.
		g.fillRect(
			BORDER_SIZE_OFFSET + BUTTON_X_ORIGIN,
			TITLE_BAR_OFFSET + START_Y_ORIGIN,
			BUTTON_WIDTH,
			BUTTON_HEIGHT
		);
		g.fillRect(
			BORDER_SIZE_OFFSET + BUTTON_X_ORIGIN,
			TITLE_BAR_OFFSET + RANDOM_Y_ORIGIN,
			BUTTON_WIDTH,
			BUTTON_HEIGHT
		);
		g.fillRect(
			BORDER_SIZE_OFFSET + BUTTON_X_ORIGIN,
			TITLE_BAR_OFFSET + SAVE_Y_ORIGIN,
			BUTTON_WIDTH,
			BUTTON_HEIGHT
		);
		g.fillRect(
			BORDER_SIZE_OFFSET + BUTTON_X_ORIGIN,
			TITLE_BAR_OFFSET + LOAD_Y_ORIGIN,
			BUTTON_WIDTH,
			BUTTON_HEIGHT
		);
	}
	
	private void saveGame() {
		StringBuilder[] gameData = new StringBuilder[40];
		
		for (int y = 0; y < 40; y++) {
			gameData[y] = new StringBuilder();
			for (int x = 0; x < 40; x++) {
				// I save the game data in a way that is more obvious and human-readable.
				if (liveCellsOnScreen[y][x]) {
					gameData[y].append('X'); // 'X' represents live cells.
				} else {
					gameData[y].append('.'); // '.' represents dead cells.
				}
			}
		}
		
		try {
			BufferedWriter gameSaver = new BufferedWriter(new FileWriter(saveFileName));
			for (StringBuilder line : gameData) {
				gameSaver.write(line.toString());
				gameSaver.newLine();
			}
			gameSaver.close(); // Move to finally block?
		} catch (IOException ex) { ex.printStackTrace(); }
	}

	private void loadGame() {
		try {
			BufferedReader gameLoader = new BufferedReader(new FileReader(saveFileName));
			
			for (int y = 0; y < 40; y++) {
				String currentLine = gameLoader.readLine();
				for (int x = 0; x < 40; x++) {
					if (currentLine.charAt(x) == 'X') {
						liveCellsOnScreen[y][x] = true;
					} else {
						liveCellsOnScreen[y][x] = false;
					}
				}
			}
			gameLoader.close();
		} catch (IOException ex) { ; }
	}

	private void randomGrid() {
		Random r = new Random();
		// This will range between 0.1 and 0.35 so each time the random button is pressed there
		// will be more variety in the number of live cells (while still tending to be small).
		double probabilityLiveCell = 0.35 - r.nextDouble() / 4;
		
		for (int i = 0; i < 40; i++) {
			for (int j = 0; j < 40; j++) {
				if (r.nextDouble() < probabilityLiveCell) {
					liveCellsOnScreen[i][j] = true;
				} else {
					liveCellsOnScreen[i][j] = false;
				}
			}
		}
	}

	// I also noticed some redundant if-statements in the
	// next four methods here and changed them as well for GitHub.
	private boolean isStartClicked(Point mousePosition) {
		return mousePosition.x - BORDER_SIZE_OFFSET >= BUTTON_X_ORIGIN &&
				mousePosition.x - BORDER_SIZE_OFFSET <= BUTTON_X_ORIGIN + BUTTON_WIDTH &&
				mousePosition.y - TITLE_BAR_OFFSET >= START_Y_ORIGIN &&
				mousePosition.y - TITLE_BAR_OFFSET <= START_Y_ORIGIN + BUTTON_HEIGHT;
	}
	
	private boolean isRandomClicked(Point mousePosition) {
		return mousePosition.x - BORDER_SIZE_OFFSET >= BUTTON_X_ORIGIN &&
				mousePosition.x - BORDER_SIZE_OFFSET <= BUTTON_X_ORIGIN + BUTTON_WIDTH &&
				mousePosition.y - TITLE_BAR_OFFSET >= RANDOM_Y_ORIGIN &&
				mousePosition.y - TITLE_BAR_OFFSET <= RANDOM_Y_ORIGIN + BUTTON_HEIGHT;
	}
	
	private boolean isSaveClicked(Point mousePosition) {
		return mousePosition.x - BORDER_SIZE_OFFSET >= BUTTON_X_ORIGIN &&
				mousePosition.x - BORDER_SIZE_OFFSET <= BUTTON_X_ORIGIN + BUTTON_WIDTH &&
				mousePosition.y - TITLE_BAR_OFFSET >= SAVE_Y_ORIGIN &&
				mousePosition.y - TITLE_BAR_OFFSET <= SAVE_Y_ORIGIN + BUTTON_HEIGHT;
	}
	
	private boolean isLoadClicked(Point mousePosition) {
		return mousePosition.x - BORDER_SIZE_OFFSET >= BUTTON_X_ORIGIN &&
				mousePosition.x - BORDER_SIZE_OFFSET <= BUTTON_X_ORIGIN + BUTTON_WIDTH &&
				mousePosition.y - TITLE_BAR_OFFSET >= LOAD_Y_ORIGIN &&
				mousePosition.y - TITLE_BAR_OFFSET <= LOAD_Y_ORIGIN + BUTTON_HEIGHT;
	}
	
	public static void main(String[] args) {
		new GameOfLife();
	}
}
