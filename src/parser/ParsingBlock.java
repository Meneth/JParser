package parser;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ParsingBlock {
	private static final Map<String, String> statements = new HashMap<>();
	private final String type;
	private final ParsingBlock parent;
	private final int nesting;
	private final List<String> contents;
	private final Collection<String> output;

	public ParsingBlock(String type, ParsingBlock parent,
			List<String> contents, int nesting, Collection<String> output) {
		this.contents = contents;
		this.output = output;
		this.type = type;
		this.parent = parent;
		this.nesting = nesting;
		parseBlock();
	}

	/**
	 * Recursively parses a block of code
	 */
	private void parseBlock() {
		int localNesting = 0;
		int i = 0, start = -1;
		if (type != null)
			output(type, output, nesting - 1);
		String type = null;
		for (String s : contents) {
			i++; // Done at the start so that the line defining the start of a
					// block doesn't get included when parsing recursively
			if (s.endsWith("{")) {
				localNesting++;
				if (start == -1) {
					type = getType(s);
					start = i;
				}
			} else if (s.equals("}")) {
				localNesting--;
				if (localNesting == 0) {
					// When local nesting is back down to 0, a full block has
					// necessarily been iterated through
					// So that block can then be recursively parsed
					new ParsingBlock(type, this, contents.subList(start, i),
							nesting + 1, output);
					start = -1;
				}
			} else {
				if (localNesting == 0) {
					output(s, output, nesting);
				}
			}
		}
	}

	private String getType(String s) {
		int index = s.indexOf('=');
		if (index == -1)
			return s;
		else
			return s.substring(0, index).trim();
	}

	private static void output(String s, Collection<String> output, int nesting) {
		Token token;
		int index = s.indexOf('=');
		if (index == -1)
			token = new Token(s);
		else
			token = new Token(s.substring(0, index).trim(), s.substring(
					index + 1).trim());
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < nesting; i++) {
			builder.append('*');
		}
		if (nesting != 0)
			builder.append(" ");
		builder.append(token.toString());
		output.add(builder.toString());
	}

	static String getStatement(String statement) {
		return statements.get(statement);
	}

	private Collection<String> getOutput() {
		return output;
	}

	public static void main(String[] args) {
		try {
			IO.readLocalisation("statements/statements.txt", statements);
			LinkedList<String> list = IO.readFile("cleanup.txt");
			Collection<String> output = new ParsingBlock(null, null, list, 0,
					new LinkedList<String>()).getOutput();
			for (String string : output) {
				System.out.println(string);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

class Token {
	private final String type;
	private final String value;

	public Token(String type) {
		super();
		this.type = type;
		value = null;
	}

	public Token(String type, String value) {
		super();
		this.type = type;
		this.value = value;
	}

	public String toString() {
		return lookup(type, value);
	}

	private String lookup(String type, String value) {
		String output = ParsingBlock.getStatement(type);
		if (output == null)
			return type + ": " + value;
		if (output.contains("%s"))
			return String.format(output, value);
		return output;
	}
}