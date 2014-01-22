/*****************************************************************************
 *                                                                           *
 *  This file is part of the BeanShell Java Scripting distribution.          *
 *  Documentation and updates may be found at http://www.beanshell.org/      *
 *                                                                           *
 *  Sun Public License Notice:                                               *
 *                                                                           *
 *  The contents of this file are subject to the Sun Public License Version  *
 *  1.0 (the "License"); you may not use this file except in compliance with *
 *  the License. A copy of the License is available at http://www.sun.com    *
 *                                                                           *
 *  The Original Code is BeanShell. The Initial Developer of the Original    *
 *  Code is Pat Niemeyer. Portions created by Pat Niemeyer are Copyright     *
 *  (C) 2000.  All Rights Reserved.                                          *
 *                                                                           *
 *  GNU Public License Notice:                                               *
 *                                                                           *
 *  Alternatively, the contents of this file may be used under the terms of  *
 *  the GNU Lesser General Public License (the "LGPL"), in which case the    *
 *  provisions of LGPL are applicable instead of those above. If you wish to *
 *  allow use of your version of this file only under the  terms of the LGPL *
 *  and not to allow others to use your version of this file under the SPL,  *
 *  indicate your decision by deleting the provisions above and replace      *
 *  them with the notice and other provisions required by the LGPL.  If you  *
 *  do not delete the provisions above, a recipient may use your version of  *
 *  this file under either the SPL or the LGPL.                              *
 *                                                                           *
 *  Patrick Niemeyer (pat@pat.net)                                           *
 *  Author of Learning Java, O'Reilly & Associates                           *
 *  http://www.pat.net/~pat/                                                 *
 *                                                                           *
 *****************************************************************************/

package nightcode.ui;

import java.awt.Component;
import java.awt.Font;
import java.awt.Color;
import java.awt.Insets;
import java.awt.event.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.util.Vector;
import java.awt.Cursor;
import javax.swing.text.*;
import javax.swing.*;
import clojure.lang.IFn;

/**
 * A JFC/Swing based console for the BeanShell desktop.
 * This is a descendant of the old AWTConsole.
 * <p/>
 * Improvements by: Mark Donszelmann <Mark.Donszelmann@cern.ch>
 * including Cut & Paste
 * <p/>
 * Improvements by: Daniel Leuck
 * including Color and Image support, key press bug workaround
 */
public class JConsole extends JScrollPane
	implements Runnable, KeyListener,
	MouseListener, ActionListener, PropertyChangeListener {
	private final static String CUT = "Cut";
	private final static String COPY = "Copy";
	private final static String PASTE = "Paste";

	private IFn interruptFunction;
	private IFn eofFunction;
	private Writer outPipe;
	private Reader inPipe;
	private Reader in;
	private PrintWriter out;

	public Reader getIn() {
		return in;
	}

	public PrintWriter getOut() {
		return out;
	}

	public PrintWriter getErr() {
		return out;
	}

	private int cmdStart = 0;
	private Vector<String> history = new Vector<String>();
	private String startedLine;
	private int histLine = 0;
	private final int HIST_MAX = 1000000;

	private JPopupMenu menu;
	private JTextPane text;
	private DefaultStyledDocument doc;

	NameCompletion nameCompletion;
	private final int SHOW_AMBIG_MAX = 10;

	// hack to prevent key repeat for some reason?
	private boolean gotUp = true;

	public JConsole() {
		this(null, null, new Font("Monospaced", Font.PLAIN, 14));
	}

	public JConsole(Font font) {
		this(null, null, font);
	}

	public JConsole(Reader cin, Writer cout, Font font) {
		super();

		// Special TextPane which catches for cut and paste, both L&F keys and
		// programmatic	behaviour
		text = new JTextPane(doc = new DefaultStyledDocument()) {
			public void cut() {
				if (text.getCaretPosition() < cmdStart) {
					super.copy();
				} else {
					super.cut();
				}
			}

			public void paste() {
				forceCaretMoveToEnd();
				super.paste();
			}
		};

		text.setFont(font);
		text.setText("");
		text.setMargin(new Insets(7, 5, 7, 5));
		text.addKeyListener(this);
		setViewportView(text);

		// create popup	menu
		menu = new JPopupMenu("JConsole	Menu");
		menu.add(new JMenuItem(CUT)).addActionListener(this);
		menu.add(new JMenuItem(COPY)).addActionListener(this);
		menu.add(new JMenuItem(PASTE)).addActionListener(this);

		text.addMouseListener(this);

		// make	sure popup menu	follows	Look & Feel
		UIManager.addPropertyChangeListener(this);

		requestFocus();
	}

	public void init() {
		try {
			if (outPipe != null) {
			 	outPipe.close();
			}
			if (out != null) {
				in.close();
			}
			if (inPipe != null) {
				inPipe.close();
			}
			if (in != null) {
				in.close();
			}
		} catch (IOException e) {}
		
		outPipe = new PipedWriter();
		try {
			in = new PipedReader((PipedWriter) outPipe);
		} catch (IOException e) {
			print("Console internal	error (1)...", Color.red);
		}

		PipedWriter pout = new PipedWriter();
		out = new PrintWriter(pout);
		try {
			inPipe = new PipedReader(pout);
		} catch (IOException e) {
			print("Console internal error: " + e);
		}
		
		new Thread(this).start();
	}

	public void setInterruptFunction(IFn interruptFunction) {
		this.interruptFunction = interruptFunction;
	}

	public void setEOFFunction(IFn eofFunction) {
		this.eofFunction = eofFunction;
	}

	public void requestFocus() {
		super.requestFocus();
		text.requestFocus();
	}

	public void keyPressed(KeyEvent e) {
		type(e);
		gotUp = false;
	}

	public void keyTyped(KeyEvent e) {
		type(e);
	}

	public void keyReleased(KeyEvent e) {
		gotUp = true;
		type(e);
	}

	private synchronized void type(KeyEvent e) {
		switch (e.getKeyCode()) {
			case (KeyEvent.VK_ENTER):
				if (e.getID() == KeyEvent.KEY_PRESSED) {
					enter();
					resetCommandStart();
					text.setCaretPosition(cmdStart);
				}
				e.consume();
				text.repaint();
				break;

			case (KeyEvent.VK_UP):
				if (e.getID() == KeyEvent.KEY_PRESSED) {
					historyUp();
				}
				e.consume();
				break;

			case (KeyEvent.VK_DOWN):
				if (e.getID() == KeyEvent.KEY_PRESSED) {
					historyDown();
				}
				e.consume();
				break;

			case (KeyEvent.VK_LEFT):
				if (text.getCaretPosition() <= cmdStart) {
					e.consume();
				}
				break;

			case (KeyEvent.VK_BACK_SPACE):
				if (text.getSelectedText() == null) {
					if(text.getCaretPosition() <= cmdStart) {
						e.consume();
					}
				} else {
					if(text.getCaretPosition() < cmdStart) {
						e.consume();
					}
					// TODO: prevent deletion when the caret is at
					// the end of the user=> marker
				}
				// See also default: case for backspace workaround
				break;

			case (KeyEvent.VK_DELETE):
				if (text.getCaretPosition() < cmdStart) {
					e.consume();
				}
				// TODO: prevent deletion when the caret is at
				// the end of the user=> marker
				break;

			case (KeyEvent.VK_RIGHT):
				forceCaretMoveToStart();
				break;

			case (KeyEvent.VK_HOME):
				if ((e.getModifiers() & InputEvent.SHIFT_MASK) > 0) {
					text.moveCaretPosition(cmdStart);
				} else {
					text.setCaretPosition(cmdStart);
				}
				e.consume();
				break;

			case (KeyEvent.VK_U):      // clear line
				if ((e.getModifiers() & InputEvent.CTRL_MASK) > 0) {
					replaceRange("", cmdStart, textLength());
					histLine = 0;
					e.consume();
				}
				break;

			case (KeyEvent.VK_D):      // "end of input"
				if ((e.getModifiers() & InputEvent.CTRL_MASK) > 0) {
					e.consume();
					if(this.eofFunction != null) {
						this.eofFunction.invoke();
					}
				}
				break;

			case (KeyEvent.VK_ALT):
			case (KeyEvent.VK_CAPS_LOCK):
			case (KeyEvent.VK_CONTROL):
			case (KeyEvent.VK_META):
			case (KeyEvent.VK_SHIFT):
			case (KeyEvent.VK_PRINTSCREEN):
			case (KeyEvent.VK_SCROLL_LOCK):
			case (KeyEvent.VK_PAUSE):
			case (KeyEvent.VK_INSERT):
			case (KeyEvent.VK_F1):
			case (KeyEvent.VK_F2):
			case (KeyEvent.VK_F3):
			case (KeyEvent.VK_F4):
			case (KeyEvent.VK_F5):
			case (KeyEvent.VK_F6):
			case (KeyEvent.VK_F7):
			case (KeyEvent.VK_F8):
			case (KeyEvent.VK_F9):
			case (KeyEvent.VK_F10):
			case (KeyEvent.VK_F11):
			case (KeyEvent.VK_F12):
			case (KeyEvent.VK_ESCAPE):

				// only	modifier pressed
				break;

			// Control-C
			case (KeyEvent.VK_C):
				if (text.getSelectedText() == null) { // Ctrl-C also copies text
					if (((e.getModifiers() & InputEvent.CTRL_MASK) > 0)
						&& (e.getID() == KeyEvent.KEY_PRESSED)) {
						//append("^C");
						if(interruptFunction != null) {
							interruptFunction.invoke("User pressed Ctrl-C");
						}
					}
					e.consume();
				}
				break;

			case (KeyEvent.VK_TAB):
				if (e.getID() == KeyEvent.KEY_RELEASED) {
					String part = text.getText().substring(cmdStart);
					doCommandCompletion(part);
				}
				e.consume();
				break;

			default:
				if (
					(e.getModifiers() &
						(InputEvent.CTRL_MASK
							| InputEvent.ALT_MASK | InputEvent.META_MASK)) == 0) {
					// plain character
					forceCaretMoveToEnd();
				}

				/*
				The getKeyCode function always returns VK_UNDEFINED for
				keyTyped events, so backspace is not fully consumed.
				*/
				if (e.paramString().indexOf("Backspace") != -1) {
					if (text.getCaretPosition() <= cmdStart) {
						e.consume();
						break;
					}
				}

				break;
		}
	}

	private void doCommandCompletion(String part) {
		if (nameCompletion == null) {
			return;
		}

		int i = part.length() - 1;

		// Character.isJavaIdentifierPart()  How convenient for us!! 
		while (
			i >= 0 &&
				(Character.isJavaIdentifierPart(part.charAt(i))
					|| part.charAt(i) == '.')
			) {
			i--;
		}

		part = part.substring(i + 1);

		if (part.length() < 2)  // reasonable completion length
		{
			return;
		}

		//System.out.println("completing part: "+part);

		// no completion
		String[] complete = nameCompletion.completeName(part);
		if (complete.length == 0) {
			java.awt.Toolkit.getDefaultToolkit().beep();
			return;
		}

		// Found one completion (possibly what we already have)
		if (complete.length == 1 && !complete.equals(part)) {
			String append = complete[0].substring(part.length());
			append(append);
			return;
		}

		// Found ambiguous, show (some of) them

		String line = text.getText();
		String command = line.substring(cmdStart);
		// Find prompt
		for (i = cmdStart; line.charAt(i) != '\n' && i > 0; i--) {
			;
		}
		String prompt = line.substring(i + 1, cmdStart);

		// Show ambiguous
		StringBuffer sb = new StringBuffer("\n");
		for (i = 0; i < complete.length && i < SHOW_AMBIG_MAX; i++) {
			sb.append(complete[i] + "\n");
		}
		if (i == SHOW_AMBIG_MAX) {
			sb.append("...\n");
		}

		print(sb, Color.gray);
		print(prompt); // print resets command start
		append(command); // append does not reset command start
	}

	private void resetCommandStart() {
		cmdStart = textLength();
	}

	private void append(String string) {
		int slen = textLength();
		text.select(slen, slen);
		text.replaceSelection(string);

		try {
			int overLength = slen + string.length() - HIST_MAX;
			if (overLength > 0) {
				text.getDocument().remove(0, overLength);
			}
		} catch (Exception e) {}
	}

	private String replaceRange(Object s, int start, int end) {
		String st = s.toString();
		text.select(start, end);
		text.replaceSelection(st);
		//text.repaint();
		return st;
	}

	private void forceCaretMoveToEnd() {
		if (text.getCaretPosition() < cmdStart) {
			// move caret first!
			text.setCaretPosition(textLength());
		}
		text.repaint();
	}

	private void forceCaretMoveToStart() {
		if (text.getCaretPosition() < cmdStart) {
			// move caret first!
		}
		text.repaint();
	}


	private void enter() {
		String s = getCmd();

		if (s.length() == 0)      // special hack	for empty return!
		{
			s = ";\n";
		} else {
			history.addElement(s);
			s = s + "\n";
		}

		append("\n");
		histLine = 0;
		acceptLine(s);
		text.repaint();
	}

	private String getCmd() {
		String s = "";
		try {
			s = text.getText(cmdStart, textLength() - cmdStart);
		} catch (BadLocationException e) {
			// should not happen
			System.out.println("Internal JConsole Error: " + e);
		}
		return s;
	}

	private void historyUp() {
		if (history.size() == 0) {
			return;
		}
		if (histLine == 0)  // save current line
		{
			startedLine = getCmd();
		}
		if (histLine < history.size()) {
			histLine++;
			showHistoryLine();
		}
	}

	private void historyDown() {
		if (histLine == 0) {
			return;
		}

		histLine--;
		showHistoryLine();
	}

	private void showHistoryLine() {
		String showline;
		if (histLine == 0) {
			showline = startedLine;
		} else {
			showline = (String) history.elementAt(history.size() - histLine);
		}

		replaceRange(showline, cmdStart, textLength());
		text.setCaretPosition(textLength());
		text.repaint();
	}

	String ZEROS = "000";

	private void acceptLine(String line) {
		//FIXME: what did this do?
		//line = buf.toString();

		if (outPipe == null) {
			print("Console internal	error: cannot output ...", Color.red);
		} else {
			try {
				outPipe.write(line);
				outPipe.flush();
			} catch (IOException e) {
				outPipe = null;
				throw new RuntimeException("Console pipe broken...");
			}
		}
		//text.repaint();
	}

	public void enterLine(String line) {
		acceptLine(line);
		enter();
	}

	public void println(Object o) {
		print(String.valueOf(o) + "\n");
		text.repaint();
	}

	public void print(final Object o) {
		invokeAndWait(new Runnable() {
			public void run() {
				append(String.valueOf(o));
				resetCommandStart();
				text.setCaretPosition(cmdStart);
			}
		});
	}

	/**
	 * Prints "\\n" (i.e. newline)
	 */
	public void println() {
		print("\n");
		text.repaint();
	}

	public void error(Object o) {
		print(o, Color.red);
	}

	public void println(Icon icon) {
		print(icon);
		println();
		text.repaint();
	}

	public void print(final Icon icon) {
		if (icon == null) {
			return;
		}

		invokeAndWait(new Runnable() {
			public void run() {
				text.insertIcon(icon);
				resetCommandStart();
				text.setCaretPosition(cmdStart);
			}
		});
	}

	public void print(Object s, Font font) {
		print(s, font, null);
	}

	public void print(Object s, Color color) {
		print(s, null, color);
	}

	public void print(final Object o, final Font font, final Color color) {
		invokeAndWait(new Runnable() {
			public void run() {
				AttributeSet old = getStyle();
				setStyle(font, color);
				append(String.valueOf(o));
				resetCommandStart();
				text.setCaretPosition(cmdStart);
				setStyle(old, true);
			}
		});
	}

	public void print(
		Object s,
		String fontFamilyName,
		int size,
		Color color
	) {

		print(s, fontFamilyName, size, color, false, false, false);
	}

	public void print(
		final Object o,
		final String fontFamilyName,
		final int size,
		final Color color,
		final boolean bold,
		final boolean italic,
		final boolean underline
	) {
		invokeAndWait(new Runnable() {
			public void run() {
				AttributeSet old = getStyle();
				setStyle(fontFamilyName, size, color, bold, italic, underline);
				append(String.valueOf(o));
				resetCommandStart();
				text.setCaretPosition(cmdStart);
				setStyle(old, true);
			}
		});
	}

	private AttributeSet setStyle(Font font) {
		return setStyle(font, null);
	}

	private AttributeSet setStyle(Color color) {
		return setStyle(null, color);
	}

	private AttributeSet setStyle(Font font, Color color) {
		if (font != null) {
			return setStyle(font.getFamily(), font.getSize(), color,
				font.isBold(), font.isItalic(),
				StyleConstants.isUnderline(getStyle()));
		} else {
			return setStyle(null, -1, color);
		}
	}

	private AttributeSet setStyle(
		String fontFamilyName, int size, Color color) {
		MutableAttributeSet attr = new SimpleAttributeSet();
		if (color != null) {
			StyleConstants.setForeground(attr, color);
		}
		if (fontFamilyName != null) {
			StyleConstants.setFontFamily(attr, fontFamilyName);
		}
		if (size != -1) {
			StyleConstants.setFontSize(attr, size);
		}

		setStyle(attr);

		return getStyle();
	}

	private AttributeSet setStyle(
		String fontFamilyName,
		int size,
		Color color,
		boolean bold,
		boolean italic,
		boolean underline
	) {
		MutableAttributeSet attr = new SimpleAttributeSet();
		if (color != null) {
			StyleConstants.setForeground(attr, color);
		}
		if (fontFamilyName != null) {
			StyleConstants.setFontFamily(attr, fontFamilyName);
		}
		if (size != -1) {
			StyleConstants.setFontSize(attr, size);
		}
		StyleConstants.setBold(attr, bold);
		StyleConstants.setItalic(attr, italic);
		StyleConstants.setUnderline(attr, underline);

		setStyle(attr);

		return getStyle();
	}

	private void setStyle(AttributeSet attributes) {
		setStyle(attributes, false);
	}

	private void setStyle(AttributeSet attributes, boolean overWrite) {
		text.setCharacterAttributes(attributes, overWrite);
	}

	private AttributeSet getStyle() {
		return text.getCharacterAttributes();
	}

	public void setFont(Font font) {
		super.setFont(font);

		if (text != null) {
			text.setFont(font);
		}
	}

	private void inPipeWatcher() throws IOException {
		char[] ca = new char[256];
		int read;
		while ((read = inPipe.read(ca)) != -1) {
			print(new String(ca, 0, read));
		}
	}

	public void run() {
		try {
			inPipeWatcher();
		} catch (IOException e) {
		}
	}

	public String toString() {
		return "BeanShell console";
	}

	// MouseListener Interface
	public void mouseClicked(MouseEvent event) {
	}

	public void mousePressed(MouseEvent event) {
		if (event.isPopupTrigger()) {
			menu.show(
				(Component) event.getSource(), event.getX(), event.getY());
		}
	}

	public void mouseReleased(MouseEvent event) {
		if (event.isPopupTrigger()) {
			menu.show((Component) event.getSource(), event.getX(),
				event.getY());
		}
		text.repaint();
	}

	public void mouseEntered(MouseEvent event) {
	}

	public void mouseExited(MouseEvent event) {
	}

	// property	change
	public void propertyChange(PropertyChangeEvent event) {
		if (event.getPropertyName().equals("lookAndFeel")) {
			SwingUtilities.updateComponentTreeUI(menu);
		}
	}

	// handle cut, copy	and paste
	public void actionPerformed(ActionEvent event) {
		String cmd = event.getActionCommand();
		if (cmd.equals(CUT)) {
			text.cut();
		} else if (cmd.equals(COPY)) {
			text.copy();
		} else if (cmd.equals(PASTE)) {
			text.paste();
		}
	}

	/**
	 * If not in the event thread run via SwingUtilities.invokeAndWait()
	 */
	private void invokeAndWait(Runnable run) {
		if (!SwingUtilities.isEventDispatchThread()) {
			try {
				SwingUtilities.invokeAndWait(run);
			} catch (Exception e) {
				// shouldn't happen
				e.printStackTrace();
			}
		} else {
			run.run();
		}
	}

	public void setNameCompletion(NameCompletion nc) {
		this.nameCompletion = nc;
	}

	public void setWaitFeedback(boolean on) {
		if (on) {
			setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
		} else {
			setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
		}
	}

	private int textLength() {
		return text.getDocument().getLength();
	}
}