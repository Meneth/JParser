package parser;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class ParsingBlock {
	private static final Map<String, String> exceptions = new HashMap<>();
	private static final String header = "==";
	private final String type;
	private final ParsingBlock parent;
	private int nesting;
	private final List<String> contents;
	private final Collection<String> output;
	private boolean inversion;
	private boolean inversionOverride = false;

	public ParsingBlock(String type, ParsingBlock parent,
			List<String> contents, int nesting, Collection<String> output, boolean negative) {
		this.contents = contents;
		this.output = output;
		this.type = type;
		this.parent = parent;
		this.nesting = nesting;
		this.inversion = negative;
		parseBlock();
	}

	/**
	 * Recursively parses a block of code
	 */
	private void parseBlock() {
		// TODO: Handle inversion of scopes
		int localNesting = 0;
		int i = 0, start = -1;
		if (type != null && nesting > 1) {
			if (exceptions.containsKey(type) && exceptions.get(type).equals("specialCommands")) {
				String v1 = null;
				String v2 = null;
				String type = Token.tokenize(this.type, inversion).type;
				for (String s : contents) {
					if (s.equals("}"))
						break;
					Token token = Token.tokenize(s, false);
					String pos = exceptions.get(token.type);
					if (pos == null) {
						System.out.println(token.type + " is not in the exceptions list!");
					}
					else if (pos.equals("value1"))
						v1 = token.value;
					else
						v2 = token.value;
				}
				// TODO: Ensure localisation lookup is done
				String s = String.format(Token.getStatement(type), v1, v2);
				output(s, output, nesting - 1);
				return; // Nothing more to do
			}
			if (isInversion(type)) {
				inversion = !inversion;
				nesting--;
			}
			else {
				output(Token.tokenize(type, inversion).toString(), output, nesting - 1);
				// The following will indicate that inversion applies to everything nested below them,
				// so inversion is overridden
				if (inversion && overridesInversion(type)) {
					inversion = false;
					inversionOverride = true;
				}
			}
		}
		String localType = null;
		for (String s : contents) {
			i++; // Done at the start so that the line defining the start of a
					// block doesn't get included when parsing recursively
			if (s.endsWith("{")) {
				localNesting++;
				if (start == -1) {
					localType = Token.tokenize(s, false).type;
					start = i;
				}
			} else if (s.equals("}")) {
				localNesting--;
				if (localNesting == 0) {
					// When local nesting is back down to 0, a full block has
					// necessarily been iterated through
					// So that block can then be recursively parsed
					new ParsingBlock(localType, this, contents.subList(start, i),
							nesting + 1, output, inversion);
					start = -1;
					if (this.type != null && isInversion(this.type))
						inversion = !inversion;
					else if (inversionOverride)
						inversion = true;
				}
			} else {
				if (localNesting == 0) {
					Token t = Token.tokenize(s, inversion);
					if (nesting > 1)
						output(t.toString(), output, nesting);
					else if (t.type.equals("title"))
						output(t.toString(), output, nesting - 1);
				}
			}
		}
	}

	private boolean isInversion(String type) {
		return type.equals("not") || type.equals("nor");
	}
	
	private boolean overridesInversion(String type) {
		return type.startsWith("any_") || type.equals("or") || type.equals("and");
	}

	private static void output(String s, Collection<String> output, int nesting) {
		if (nesting == 0) {
			output.add("\n" + header + " " + s + " " + header);
			return;
		}
		StringBuilder builder = new StringBuilder();
		if (nesting == 1) {
			builder.append("\n");
		}
		for (int i = 0; i < nesting; i++) {
			builder.append('*');
		}
		if (nesting != 0)
			builder.append(" ");
		builder.append(s);
		output.add(builder.toString());
	}

	private Collection<String> getOutput() {
		return output;
	}

	public static void main(String[] args) {
		try {
			Token.initialize("E:\\Steam\\SteamApps\\common\\Europa Universalis IV");
			IO.readExceptions("statements/exceptions.txt", exceptions);
			LinkedList<String> list = IO.readFile("cleanup.txt");
			Collection<String> output = new ParsingBlock(null, null, list, 0,
					new LinkedList<String>(), false).getOutput();
			for (String string : output) {
				System.out.println(string);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}

