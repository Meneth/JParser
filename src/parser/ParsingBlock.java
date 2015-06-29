package parser;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ParsingBlock {
	private static final Map<String, String> exceptions = new HashMap<>();
	private static final Map<String, Iterable<String>> modifiers = new HashMap<>();
	private final String type;
	private final ParsingBlock parent;
	private int nesting;
	private final List<String> contents;
	private final Collection<String> output;
	private boolean inversion;
	private boolean inversionOverride = false;

	public ParsingBlock(String type, ParsingBlock parent, List<String> contents, int nesting,
			Collection<String> output, boolean negative) {
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
		if (isSpecialCommand(type)) {
			handleSpecialCommand();
			return; // Nothing more to do
		}
		handleBlockType();
		handleBlockContents();
	}

	/**
	 * Handles all contents in the parsing block, including recursive calls
	 */
	private void handleBlockContents() {
		int localNesting = 0;
		int i = 0, start = -1;
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
					new ParsingBlock(localType, this, contents.subList(start, i), nesting + 1,
							output, inversion);
					start = -1;
					if (type != null && isInversion(type))
						inversion = !inversion;
					else if (inversionOverride)
						inversion = true;
				}
			} else {
				if (localNesting == 0) {
					Token t = Token.tokenize(s, inversion);
					if (nesting > 1 && isOutputType(t.type))
						output(t.toString(), output, nesting + 1);
					else if (t.type.equals("title"))
						output(t.toString(), output, nesting);
				}
			}
		}
	}

	/**
	 * Handles everything related to the type of the block, which is currently
	 * inversion and scope headers
	 */
	private void handleBlockType() {
		if (type != null && nesting > 1) {
			if (needsName(type)) {
				handleName();
			} else if (isInversion(type)) {
				// "NOT" in the game code means NOR, so can simply be handled by
				// inverting everything within a block
				inversion = !inversion;
				nesting--;
			} else {
				output(Token.tokenize(type, inversion).toString(), output, nesting);
				// The following will indicate that inversion applies to
				// everything nested below them,
				// so inversion is overridden
				if (inversion && overridesInversion(type)) {
					inversion = false;
					inversionOverride = true;
				}
			}
		}
	}

	/**
	 * Determines if a given type of token should be output
	 * 
	 * @param type
	 *            The type of token
	 * @return Whether it should be output
	 */
	private static boolean isOutputType(String type) {
		return !isName(type);
	}

	/**
	 * Collects and outputs the name of a section
	 */
	private void handleName() {
		for (String s : contents) {
			Token token = Token.tokenize(s, false);
			if (isName(token.type)) {
				token = new Token(type, token.value, false);
				output(token.toString(), output, nesting);
				return;
			}
		}
		throw new IllegalStateException("No valid name found.");
	}

	private static final Set<String> BLOCKNAMES = new HashSet<String>(Arrays.asList(new String[] {
			"factor", "name" }));

	private static boolean isName(String type) {
		return BLOCKNAMES.contains(type);
	}

	private static final Set<String> NAMEDBLOCKS = new HashSet<String>(Arrays.asList(new String[] {
			"option", "modifier", "ai_chance" }));

	private static boolean needsName(String type) {
		return NAMEDBLOCKS.contains(type);
	}

	/**
	 * Handles commands that go across multiple lines but need to be merged into
	 * a single line plus potential modifiers
	 */
	private void handleSpecialCommand() {
		String v1 = null;
		String v2 = null;
		String modifier = null;
		String type = Token.tokenize(this.type, inversion).type;
		for (String s : contents) {
			if (s.equals("}"))
				break;
			Token token = Token.tokenize(s, false);
			String pos = exceptions.get(token.type);
			if (pos == null) {
				System.out.println(token.type + " is not in the exceptions list!");
			} else if (pos.equals("value1"))
				v1 = token.getLocalisedValue();
			else
				v2 = token.getLocalisedValue();
			if (token.type.equals("name")) {
				modifier = token.value.replace("\"", "");
			}
		}
		output(String.format(Token.getStatement(type), v1, v2), output, nesting);
		if (modifier != null) {
			for (String effect : modifiers.get(modifier)) {
				output(effect, output, nesting + 1);
			}
		}
	}

	private static boolean isSpecialCommand(String type) {
		return exceptions.containsKey(type) && exceptions.get(type).equals("specialCommands");
	}

	private static final Set<String> NEGATIONS = new HashSet<String>(Arrays.asList(new String[] {
			"not", "nor" }));

	private static boolean isInversion(String type) {
		return NEGATIONS.contains(type);
	}

	private static final Set<String> INVERSIONOVERRIDES = new HashSet<String>(
			Arrays.asList(new String[] { "option", "modifier", "ai_chance", "any_", "all_" }));
	private static final Set<String> INVERSIONOVERRIDEPREFIXES = new HashSet<String>(
			Arrays.asList(new String[] { "any_", "all_" }));

	private static boolean overridesInversion(String type) {
		boolean start = false;
		for (String s : INVERSIONOVERRIDEPREFIXES)
			if (type.startsWith(s))
				start = true;
		return start || INVERSIONOVERRIDES.contains(type);
	}

	private static final String HEADER = "\n== %s ==";
	private static final String BOLD = "\n'''%s'''\n";

	private static void output(String s, Collection<String> output, int nesting) {
		nesting = nesting - 2;
		if (nesting == -1) {
			output.add(String.format(HEADER, s));
			return;
		} else if (nesting == 0) {
			output.add(String.format(BOLD, s));
			return;
		}
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < nesting; i++) {
			builder.append('*');
		}
		if (nesting != -1)
			builder.append(" ");
		builder.append(s);
		output.add(builder.toString());
	}

	private Collection<String> getOutput() {
		return output;
	}

	public static void main(String[] args) {
		try {
			Token.initialize("E:/Steam/SteamApps/common/Europa Universalis IV");
			IO.readExceptions("statements/exceptions.txt", exceptions);
			LinkedList<String> list = IO
					.readFile("E:/Steam/SteamApps/common/Europa Universalis IV/events/CanalEvents.txt");
			Collection<String> output = new ParsingBlock(null, null, list, 0,
					new LinkedList<String>(), false).getOutput();
			for (String string : output) {
				System.out.println(string);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void parseModifiers(List<String> readFile) {
		String name = null;
		List<String> effects = new LinkedList<>();
		for (String line : readFile) {
			if (line.endsWith("{"))
				name = Token.tokenize(line, false).type;
			else if (line.equals("}")) {
				modifiers.put(name, new LinkedList<>(effects));
				effects.clear();
			} else {
				effects.add(Token.tokenize(line, false).toString());
			}
		}
	}
}