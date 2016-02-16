package parser;

import java.util.ArrayList;
import java.util.List;

import parser.Localisation.Operator;

public class Token {
	public final String type, value;
	public final Operator operator;
	public final Token parent;
	public final List<Token> children;
	public boolean inverted = false;
	public boolean disabled = false;
	
	private Token(String type, String value, Operator operator, Token parent) {
		super();
		this.type = type.toLowerCase();
		if (value == null)
			this.value = null;
		else
			this.value = value.replaceAll("^\"(.*)\"$", "$1");
		this.operator = operator;
		this.parent = parent;
		if (parent != null)
			parent.children.add(this);
		children = new ArrayList<>();
	}
	
	/**
	 * Creates a token from a given string and its parent token
	 * @param s The string to turn into a token
	 * @param parent The block it is contained within
	 * @return The created token
	 */
	public static Token tokenize(String s, Token parent) {
		Operator operator = null;
		int index = -1;
		if (s.indexOf('=') != -1) {
			operator = Operator.EQUAL;
			index = s.indexOf('=');
		} else if (s.indexOf('<') != -1) {
			operator = Operator.LESS;
			index = s.indexOf('<');
		} else if (s.indexOf('>') != -1) {
			operator = Operator.MORE;
			index = s.indexOf('>');
		}
		
		if (index == -1)
			return new Token(s, null, null, parent);
		else
			return new Token(s.substring(0, index).trim(), s.substring(index + 1).trim(), operator, parent);
	}
	
	/**
	 * Creates a token tree from a given file (after the file has gone through a first pass in the IO class)
	 * @param file The lines of the file
	 * @return The root token
	 */
	public static Token tokenize(List<String> file) {
		Token root = new Token("file", null, null, null);
		Token block = root;
		
		for (String string : file) {
			if (string.equals("}"))
				block = block.parent;
			else if (string.contains("{")) {
				block = tokenize(string, block);
			} else {
				tokenize(string, block);
			}
		}
		
		return root;
	}
	
	public String toString() {
		if (value == null)
			return type;
		else {
			char op;
			switch (operator) {
			case LESS:
				op = '<';
				break;
			case MORE:
				op = '>';
				break;
			case EQUAL:
				op = '=';
				break;
			default:
				throw new IllegalStateException("Invalid operator!");
			}
			return String.format("%s %s %s", type, op, value);
		}
	}
}