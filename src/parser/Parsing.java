package parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import parser.Localisation.Operator;

public class Parsing {
	private static final Map<String, String[]> namedBlocks = new HashMap<>();
	private static final Map<String, String[]> multiTokenExpressions = new HashMap<>();
	private static final Map<String, Iterable<String>> modifiers = new HashMap<>();

	private static void parseTree(Token token, List<String> output,
			int nesting, boolean inverted) {
		String out = null;
		boolean toOutput = true;
		
		if (token.disabled)
			return; // Skip this sub-tree
		
		if (isInversion(token.type)) {
			inverted = !inverted;
			token.inverted = inverted;
			nesting--;
			toOutput = false;
		}
		else if (inverted) {
			token.inverted = true;
			inverted = false; // Never persists past more than one level
		}
		if (token.value != null && token.value.equals("no"))
			token.inverted = !token.inverted;
		
		if (nesting == -1)
			out = "";
		else if (nesting == 0) {
			if (isBlock(token))
				out = localize(findName(token));
			else
				out = "";
		} else if (nesting == 1 && !isBlock(token))
			out = "";
		else if (isMultiTokenExpression(token)) {
			outputMultiLineCommand(token, output, nesting);
			return; // Handles its own children
		} else if (isNamedBlock(token))
			out = localize(findName(token));
		else
			out = localize(token);
		
		if (toOutput)
			output(out, output, nesting);
		
		for (Token child : token.children) {
			parseTree(child, output, nesting + 1, inverted);
		}
	}

	private static void outputMultiLineCommand(Token token, List<String> output, int nesting) {
		String[] associatedTypes = multiTokenExpressions.get(token.type);
		int length = associatedTypes.length;
		List<String> values = new LinkedList<>();
		
		// Some multi-line commands specify a modifier to be added
		String modifierName = null;
		
		Operator operator = token.operator;
		for (int i = 0; i < length; i++) {
			boolean found = false;
			String target = associatedTypes[i];
			
			for (Token child : token.children) {
				if (child.type.equals(target)) {
					values.add(localizeValue(child));
					if (child.operator != Operator.EQUAL)
						operator = child.operator;
					if (isModifier(child))
						modifierName = child.value;
					found = true;
				} else if (Localisation.variations.containsKey(child.type)) {
					String variationName = Localisation.variations.get(child.type);
					if (variationName.equals(target)) {
						values.add(Localisation.findLocalisation(child.type));
						values.add(localizeValue(child));
					}
					found = true;
				}
			}
			// Sometimes duration is left out
			if (!found && target.equals("duration"))
				values.add("the rest of the campaign");
		}
		
		output(Localisation.formatString(token.type, operator, token.inverted, (String []) values.toArray(new String[values.size()])),
				output, nesting);
		if (modifierName != null) {
			Iterable<String> effects = modifiers.get(modifierName);
			if (effects != null)
				for (String effect : effects) {
					output(effect, output, nesting + 1);
				}
		}
	}

	private static boolean isModifier(Token child) {
		// TODO - Game-independent detection
		return child.type.equals("name");
	}

	private static String localizeValue(Token token) {
		return Localisation.localizeValue(token);
	}

	private static String localize(Token token) {
		return Localisation.localize(token);
	}

	private static Token findName(Token token) {
		String[] nameTokens = namedBlocks.get(token.type);
		for (String string : nameTokens) {
			for (Token child : token.children) {
				if (string.equals(child.type)) {
					child.disabled = true;
					return child;
				}
			}
		}
		System.out.println("No name found for " + token);
		return token;
		//throw new IllegalStateException("No name found!");
	}

	private static boolean isBlock(Token token) {
		return token.children.size() > 0;
	}
	
	private static boolean isNamedBlock(Token token) {
		return isBlock(token) && namedBlocks.containsKey(token.type);
	}
	
	private static boolean isMultiTokenExpression(Token token) {
		return isBlock(token) && multiTokenExpressions.containsKey(token.type);
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
	
	private static void output(String s, List<String> output, int nesting) {
		if (s.equals(""))
			return; // Skip blank lines
		
		nesting--;
		
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
	private static void parseModifiers(Token root) {
		for (Token child : root.children) {
			List<String> effects = new LinkedList<>();
			String name = child.type;
			for (Token child2 : child.children) {
				String s = localize(child2);
				if (s.charAt(0) >= '0' && s.charAt(0) <= '9')
					s = "+" + s;
				effects.add(s);
			}
			modifiers.put(name, effects);
		}
	}

	public static void main(String[] args) throws IOException {
		HashMap<String, String> settings = new HashMap<>();
		IO.readLocalisation("settings.txt", settings);
		String path = settings.get("path");
		String game = settings.get("game").toLowerCase();
		
		Localisation.initialize(path, game);
		
		IO.readExceptions(String.format("statements/%s/namedSections.txt", game), namedBlocks);
		IO.readExceptions(String.format("statements/%s/exceptions.txt", game), multiTokenExpressions);
		if (game.equals("eu4"))
			parseModifiers(Token.tokenize(IO.readFile(path 
				+ "/common/event_modifiers/00_event_modifiers.txt")));
		
		Files.walk(Paths.get(path + "/events")).forEachOrdered(filePath -> {
			if (Files.isRegularFile(filePath)) {
				System.out.println("Parsing " + filePath.getFileName());
				try {
					List<String> list = IO.readFile(filePath.toString());
					Token root = Token.tokenize(list);
					List<String> output = new LinkedList<>();
					parseTree(root, output, -1, false);
					IO.writeFile("output/" + filePath.getFileName(), output);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});
		IO.writeFile("output/errors.txt", Localisation.errors);
	}

	// TODO - Properly handle calling other events
	// TODO - Handle event headers (E.G., is_mtth_scaled_to_size)
}
