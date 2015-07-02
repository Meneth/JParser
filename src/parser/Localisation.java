package parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class Localisation {
	private static final Map<String, String> lookupRules = new HashMap<>();
	private static final Map<String, String> localisation = new HashMap<>();
	private static final Map<String, String> statements = new HashMap<>();
	private static final Set<String> regions = new HashSet<>();
	private static final Map<String, String> variations = new HashMap<>();
	private static final Pattern country = Pattern.compile("[a-z]{3}");
	private static final Pattern noLookup = Pattern.compile("(.* .*)|\\d*");
	private static final String PROVINCE = "province";
	private static final String COUNTRY = "country";
	private static final String TIME = "time";
	public static final Set<String> errors = new HashSet<>();

	/**
	 * Loads all info needed in order to run the parser
	 * 
	 * @param path
	 *            The path to the game folder
	 */
	public static void initialize(String path) {
		try {
			Files.walk(Paths.get("statements/localisation")).forEach(filePath -> {
				if (Files.isRegularFile(filePath)) {
					try {
						IO.readLocalisation(filePath.toString(), statements, false);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});
			IO.readExceptions("statements/lookupRules.txt", lookupRules);
			Set<String> localisationFiles = new HashSet<String>(Arrays.asList(new String[] {
					"countries", "EU4", "text", "opinions", "powers_and_ideas", "decisions",
					"modifers", "muslim_dlc", "Purple_Phoenix", "core", "missions", "diplomacy",
					"flavor_events", "USA_dlc", "nw2", "sikh", "tags_phase4", "flavor_events",
					"generic_events", "aow", "prov_names", "common_sense", "eldorado" }));
			localisationFiles.forEach(file -> {
				try {
					readLocalisation(path, file);
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
			ParsingBlock.parseModifiers(IO.readFile(path
					+ "/common/event_modifiers/00_event_modifiers.txt"));
			IO.readHeaders(path + "/map/region.txt", regions, 0);
			IO.readHeaders(path + "/map/continent.txt", regions, 0);
			IO.readHeaders(path + "/common/colonial_regions/00_colonial_regions.txt", regions, 0);
			Map<String, String> variationFiles = new HashMap<>();
			IO.readLocalisation("statements/variations.txt", variationFiles, false);
			variationFiles.forEach((localisation, param) -> {
				try {
					if (localisation.startsWith("#"))
						return;
					String[] params = param.split(", ");
					Collection<String> vars = new HashSet<>();
					IO.readHeaders(path + params[0], vars, Integer.parseInt(params[1]));
					for (String string : vars) {
						variations.put(string, localisation);
						variations.put(string + "_false", localisation);
					}
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			});
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Reads a localisation file and adds all localisation to the "localisation"
	 * map
	 * 
	 * @param path
	 *            The path to the game folder
	 * @param file
	 *            The localisation file to be read
	 * @throws IOException
	 */
	private static void readLocalisation(String path, String file) throws IOException {
		IO.readLocalisation(path + "/localisation/" + file + "_l_english.yml", localisation, true);
	}

	/**
	 * Formats a token
	 * @param type The type of token
	 * @param value The value of the token
	 * @return The formatted string
	 */
	public static String formatToken(String type, String value) {
		if (variations.containsKey(type)) {
			return formatStatement(variations.get(type), type, value);
		}

		// Some tokens are localized differently if referring to a country
		if (COUNTRY.equals(lookupRules.get(type.replace("_false", "")))) {
			if (isCountry(value))
				type += "_country";
		}
		value = formatValue(type, value);

		String output = statements.get(type);
		if (output == null) {
			output = getScopeLocalisation(type);
			if (!output.equals(type))
				return output;
			return type + ": " + value;
		}
		if (value != null)
			try {
				float f = Float.parseFloat(value);
				if (output.contains("%%"))
					value = "" + (int) (f * 100);
				if (output.startsWith("%s") && f > 0)
					value = "+" + value;
			} catch (NumberFormatException e) {
			}
		return formatStatement(type, value);
	}

	private static boolean isBoolean(String value) {
		return value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("no");
	}

	/**
	 * Formats a token's value
	 * @param type The type of the token
	 * @param value The value of the token
	 * @return The formatted value
	 */
	public static String formatValue(String type, String value) {
		String targetType;
		targetType = lookupRules.get(type.replace("_false", ""));
		if (type.endsWith(COUNTRY))
			targetType = COUNTRY;
		if (value != null) {
			if (targetType != null) {
				switch (targetType) {
				case PROVINCE:
					return getProvince(value);
				case COUNTRY:
					if (!isBoolean(value)) {
						return getCountry(value);
					}
					break;
				case TIME:
					int val = Integer.parseInt(value);
					if (val == -1)
						value = "the rest of the campaign";
					else if (val < 365)
						value = (int) ((float) val / 365 * 12) + " months";
					else
						value = val / 365 + " years";
					return value;
				}
			}
			if (isLookup(value))
				return getLocalisation(value);
		}
		return value;
	}

	/**
	 * Determines whether the parser should try to look up a given token value in the game localisation
	 * @param value The token's value
	 * @return Whether it should be looked up
	 */
	private static boolean isLookup(String value) {
		return !noLookup.matcher(value).matches();
	}

	/**
	 * Determines whether a given token value refers to a country
	 * @param value The token's value
	 * @return Whether it refers to a country
	 */
	private static boolean isCountry(String value) {
		return country.matcher(value).matches() || value.equalsIgnoreCase("root")
				|| value.equalsIgnoreCase("this") || value.equalsIgnoreCase("from");
	}

	/**
	 * Attempts to find the game localisation for a given type or value
	 * @param key The type or value to be localised
	 * @return The localisation found. The key provided is returned if no localisation is found
	 */
	private static String getLocalisation(String key) {
		key = key.replace("\"", "");
		String loc = localisation.get(key);
		if (loc != null)
			return loc;
		loc = localisation.get("building_" + key);
		if (loc != null)
			return loc;
		loc = localisation.get(key + "_title");
		if (loc != null)
			return loc;
		return key;
	}

	/**
	 * Attemps to find the game localisation for a given scope
	 * @param scope The scope to localise
	 * @return The localisation found, plus formatting. The scope provided is returned if no localisation is found
	 */
	private static String getScopeLocalisation(String scope) {
		String key2 = scope.replace("_false", "");
		String loc = null;
		while (true) {
			if (regions.contains(key2))
				loc = localisation.get(key2);
			if (loc != null)
				break;
			loc = getProvince(key2);
			if (loc != null)
				break;
			if (isCountry(key2)) {
				loc = getCountry(key2);
				if (loc != null)
					break;
			}
			break;
		}
		if (loc == null) {
			errors.add(scope);
			return scope;
		}
		if (scope.endsWith("_false"))
			loc += " - not all of the following";
		loc += ":";
		return loc;
	}

	/**
	 * Looks up a province name
	 * @param id The ID of the province
	 * @return The province's name
	 */
	private static String getProvince(String id) {
		return localisation.get("prov" + id);
	}

	/**
	 * Looks up a country name
	 * @param id The ID of the country
	 * @return The country's name
	 */
	private static String getCountry(String id) {
		switch (id) {
		case "":
			return null;
		case "ROOT":
			return "our country";
		case "THIS":
			return "this country";
		case "FROM":
			return "FROM"; // TODO - Localise
		default:
			return localisation.get(id);
		}
	}

	/**
	 * Inserts any parameters of a statement at the appropriate places
	 * @param type The token type
	 * @param params The parameters (if any) of the statement
	 * @return The formatted string. The type is returned if no formatting was found
	 */
	public static String formatStatement(String type, String...params) {
		String statement = statements.get(type);
		return statement == null ? type : String.format(statement, (Object[]) params);
	}
	
	/**
	 * Gets a format string for a given token type
	 * @param type The token type
	 * @return The format string. The type is returned if no formatting was found
	 */
	public static String getStatement(String type) {
		String statement = statements.get(type);
		return statement == null ? type : statement;
	}
	
	// TODO - Handle text highlighting. E.G., §Ytrade§!. Regex might be a good solution.
}
