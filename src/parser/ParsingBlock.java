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
	private static final Map<String, String> exceptions = new HashMap<>();
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
		if (type != null) {
			if (exceptions.containsKey(type) && exceptions.get(type).equals("specialCommands")) {
				String v1 = null;
				String v2 = null;
				for (String s : contents) {
					if (s.equals("}"))
						break;
					Token token = Token.tokenize(s);
					String pos = exceptions.get(token.type);
					if (pos == null) {
						System.out.println(token.type + " is not in the exceptions list!");
					}
					else if (pos.equals("value1"))
						v1 = token.value;
					else
						v2 = token.value;
				}
				String s = String.format(getStatement(type), v1, v2);
				output(s, output, nesting);
				return; // Nothing more to do
			}
			output(Token.tokenize(type).toString(), output, nesting - 1);
		}
		String type = null;
		for (String s : contents) {
			i++; // Done at the start so that the line defining the start of a
					// block doesn't get included when parsing recursively
			if (s.endsWith("{")) {
				localNesting++;
				if (start == -1) {
					type = Token.tokenize(s).type;
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
					output(Token.tokenize(s).toString(), output, nesting);
				}
			}
		}
	}

	private static void output(String s, Collection<String> output, int nesting) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < nesting; i++) {
			builder.append('*');
		}
		if (nesting != 0)
			builder.append(" ");
		builder.append(s);
		output.add(builder.toString());
	}

	public static String getStatement(String statement) {
		return statements.get(statement);
	}

	private Collection<String> getOutput() {
		return output;
	}

	public static void main(String[] args) {
		try {
			IO.readLocalisation("statements/statements.txt", statements);
			IO.readLocalisation("statements/special.txt", statements);
			IO.readExceptions("statements/exceptions.txt", exceptions);
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

