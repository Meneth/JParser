package parser;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ParsingBlock {
	private static final Map<String, String[]> exceptions = new HashMap<>();
	private static final Map<String, String[]> parentExceptions = new HashMap<>();
	private static final Map<String, String[]> namedBlocks = new HashMap<>();
	private static final Map<String, Iterable<String>> modifiers = new HashMap<>();
	private static final String TRUE = "yes";
	private static final String FALSE = "no";
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
		if (nesting > 1 && isSpecialCommand(type)) {
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
				if (localNesting == 0 && nesting > 1) {
					Token t = Token.tokenize(s, inversion);
					if (isOutputType(t.type, type)) {
						String[] vals = parentExceptions.get(type);
						if (vals != null && Arrays.asList(vals).contains(t.baseType)) {
							t = new Token(type + "_" + t.baseType, t.value, t.operator, inversion);
						}
						output(t.toString(), output, nesting + 1);
					}
				}
			}
		}
	}

	/**
	 * Handles everything related to the type of the block, which is currently
	 * inversion and scope headers
	 */
	private void handleBlockType() {
		if (type != null) {
			if (needsName(type) || nesting == 1) {
				handleName();
				if (inversion) {
					inversion = false;
					inversionOverride = true;
				}
			} else if (nesting > 1) {
				if (isInversion(type)) {
					// "NOT" in the game code means NOR, so can simply be
					// handled by
					// inverting everything within a block
					inversion = !inversion;
					nesting--;
				} else {
					if (parentExceptions.containsKey(parent.type))
						output(Token.tokenize(type + "_" + parent.type, inversion).toString(), output, nesting);
					else
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
	}

	/**
	 * Determines if a given type of token should be output
	 * 
	 * @param type
	 *            The type of token
	 * @return Whether it should be output
	 */
	private static boolean isOutputType(String type, String parent) {
		return !isName(type, parent) || !needsName(parent);
	}

	/**
	 * Collects and outputs the name of a section
	 */
	private void handleName() {
		for (String s : contents) {
			Token token = Token.tokenize(s, false);
			if (isName(token.type, type)) {
				String out = new Token(type + "_" + token.type, token.value, token.operator, false).toString();
				output(out, output, nesting);
				return;
			}
		}
		//throw new IllegalStateException("No valid name found.");
	}

	// TODO - Properly handle calling other events

	/**
	 * Determines whether a token type should be used to name a section
	 * 
	 * @param type
	 *            The token type
	 * @return Whether it should be used to name a section
	 */
	private static boolean isName(String type, String parent) {
		if (!needsName(parent))
			return false;
		for (String s : namedBlocks.get(parent))
			if (s.equals(type))
				return true;
		return false;
	}

	/**
	 * Determines whether a section needs to fetch a name further down in the
	 * game code
	 * 
	 * @param type
	 *            The token type
	 * @return Whether it needs to fetch a name further down
	 */
	private static boolean needsName(String type) {
		return namedBlocks.containsKey(type);
	}

	/**
	 * Handles commands that go across multiple lines but need to be merged into
	 * a single line plus potential modifiers
	 */
	private void handleSpecialCommand() {
		String[] associatedValues = exceptions.get(type);
		Collection<String> values = new ArrayList<>();
		String modifier = null;
		String type = Token.tokenize(this.type, inversion).type;
		for (String val : associatedValues) {
			boolean found = false;
			for (String s : contents) {
				Token token = Token.tokenize(s, false);
				if (token.baseType.equals(val)) {
					values.add(token.getLocalisedValue());
					if (token.value.equals(FALSE))
						type = Token.tokenize(this.type, !inversion).type;
					if (token.baseType.equals("name")) {
						modifier = token.value;
					}
					found = true;
					break;
				} else if (val.equals(Localisation.getVariation(token.baseType))) {
					Token t2 = new Token(null, token.type, null, false);
					values.add(t2.getLocalisedValue());
					values.add(token.getLocalisedValue());
					found = true;
					break;
				}
			}
			if (!found && val.equals("duration"))
				values.add(" for the rest of the campaign");
		}

		output(Localisation.formatStatement(type, null, values.toArray(new String[values.size()])),
				output, nesting);
		if (modifier != null) {
			Iterable<String> effects = modifiers.get(modifier);
			if (effects != null)
				for (String effect : effects) {
					output(effect, output, nesting + 1);
				}
		}
	}

	/**
	 * Determines whether a command has to be handled differently due to
	 * spanning multiple lines
	 * 
	 * @param type
	 *            The name of the command
	 * @return Whether it has to be handled differently
	 */
	private static boolean isSpecialCommand(String type) {
		return exceptions.containsKey(type);
	}

	private static final Set<String> NEGATIONS = new HashSet<String>(Arrays.asList(new String[] {
			"not", "nor" }));

	/**
	 * Determines whether a section inverts everything within it
	 * 
	 * @param type
	 *            The section name
	 * @return Whether it inverts everything within it
	 */
	private static boolean isInversion(String type) {
		return NEGATIONS.contains(type.toLowerCase());
	}

	/**
	 * Determines whether tokens within a given section can ignore inversion
	 * specified outside it due to the inversion being applied to the section
	 * name instead
	 * 
	 * @param type
	 *            The section type
	 * @return Whether it overrides inversion
	 */
	private static boolean overridesInversion(String type) {
		return Localisation.fetchStatement(type).endsWith(":");
	}

	private static final String HEADER = "\n== %s ==";
	private static final String BOLD = "\n'''%s'''\n";

	/**
	 * Formats a string based on how deeply nested it is, and adds it to the
	 * output collection
	 * 
	 * @param s
	 *            The string to be output
	 * @param output
	 *            The collection the formatted string is to be added to
	 * @param nesting
	 *            How deeply nested the string is
	 */
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

	/**
	 * Reads all event modifiers and converts them to human-readable text, so
	 * that they can be displayed when a modifier is added
	 * 
	 * @param readFile
	 *            A formatted file containing modifiers
	 */
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

	public static void main(String[] args) throws IOException {
		try {
			File dir = new File("output");
			dir.mkdir();
			HashMap<String, String> settings = new HashMap<>();
			IO.readLocalisation("settings.txt", settings, false);
			String path = settings.get("path");
			String game = settings.get("game").toLowerCase();
			Localisation.initialize(path, game);
			IO.readExceptions(String.format("statements/%s/exceptions.txt", game), exceptions);
			IO.readExceptions(String.format("statements/%s/parentExceptions.txt", game), parentExceptions);
			IO.readExceptions(String.format("statements/%s/namedSections.txt", game), namedBlocks);
			Files.walk(Paths.get(path + "/events")).forEachOrdered(filePath -> {
				if (Files.isRegularFile(filePath)) {
					System.out.println("Parsing " + filePath.getFileName());
					try {
						LinkedList<String> list = IO.readFile(filePath.toString());
						Collection<String> output = new LinkedList<>();
						new ParsingBlock(null, null, list, 0, output, false);
						IO.writeFile("output/" + filePath.getFileName(), output);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});
			IO.writeFile("output/errors.txt", Localisation.errors);
			System.out.println("Parsing complete. Press enter to close the program");
			System.in.read();
		} catch (IOException e) {
			e.printStackTrace();
			System.out.println("Parsing failed. Press enter to close the program");
			System.in.read();
		}
	}
	
	// TODO - Handle event headers (E.G., is_mtth_scaled_to_size)
}
