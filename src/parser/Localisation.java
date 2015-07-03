package parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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
	public static final Set<String> errors = new HashSet<>();

	public static enum ValueType {
		COUNTRY, PROVINCE, DAYS, MONTHS, YEARS, OTHER;
		
		public String toString() {
	        return name().toLowerCase();
	    }
	};

	private static final ValueType getValueType(String s) {
		if (s == null)
			return ValueType.OTHER;
		return ValueType.valueOf(s.toUpperCase());
	}

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
			Files.walk(Paths.get(path + "/localisation")).forEach(file -> {
				try {
					if (((Path) file).toFile().isFile())
						if (file.toString().contains("_l_english"))
							IO.readLocalisation(file.toString(), localisation, true);
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
						variations.put(string + "_false", localisation + "_false");
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
	 * Formats a token
	 * 
	 * @param type
	 *            The type of token
	 * @param value
	 *            The value of the token
	 * @return The formatted string
	 */
	public static String formatToken(Token token) {
		if (variations.containsKey(token.type)) {
			return formatStatement(variations.get(token.type), getLocalisation(token.type),
					token.value);
		}
		String value = formatValue(token);

		String output = statements.get(token.type);
		if (output == null) {
			output = getScopeLocalisation(token.type);
			if (!output.equals(token.type))
				return output;
			return token.type + ": " + value;
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
		if (token.valueType == ValueType.COUNTRY)
			return formatStatement(token.type + "_country", value);
		return formatStatement(token.type, value);
	}

	private static boolean isBoolean(String value) {
		return value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("no");
	}

	/**
	 * Formats a token's value
	 * 
	 * @param type
	 *            The type of the token
	 * @param value
	 *            The value of the token
	 * @return The formatted value
	 */
	public static String formatValue(Token token) {
		String value = null;
		if (token.value != null) {
			int val;
			switch (token.valueType) {
			case PROVINCE:
				return getProvince(token.value);
			case COUNTRY:
				if (!isBoolean(token.value)) {
					return getCountry(token.value);
				}
				break;
			case DAYS:
				val = Integer.parseInt(token.value);
				if (val == -1)
					value = "the rest of the campaign";
				else if (val < 31)
					value = val + " days";
				else if (val < 365)
					value = (int) ((float) val / 365 * 12) + " months";
				else
					value = val / 365 + " years";
				return value;
			case MONTHS:
			case YEARS:
				val = Integer.parseInt(token.value);
				if (val == -1)
					value = "the rest of the campaign";
				else
					value = val + " " + token.valueType;
				return value;
			case OTHER:
				if (isLookup(token.value))
					return getLocalisation(token.value);
				return token.value;
			}
		}
		return value;
	}

	/**
	 * Determines whether the parser should try to look up a given token value
	 * in the game localisation
	 * 
	 * @param value
	 *            The token's value
	 * @return Whether it should be looked up
	 */
	private static boolean isLookup(String value) {
		return !noLookup.matcher(value).matches();
	}

	/**
	 * Determines whether a given token value refers to a country
	 * 
	 * @param value
	 *            The token's value
	 * @return Whether it refers to a country
	 */
	private static boolean isCountry(String value) {
		return country.matcher(value).matches() || value.equalsIgnoreCase("root")
				|| value.equalsIgnoreCase("this") || value.equalsIgnoreCase("from");
	}

	/**
	 * Attempts to find the game localisation for a given type or value
	 * 
	 * @param key
	 *            The type or value to be localised
	 * @return The localisation found. The key provided is returned if no
	 *         localisation is found
	 */
	private static String getLocalisation(String key) {
		String key2 = key.replace("\"", "");
		key2 = key2.replace("_false", "");
		String loc = localisation.get(key2);
		if (loc != null)
			return loc;
		loc = localisation.get("building_" + key2);
		if (loc != null)
			return loc;
		loc = localisation.get(key2 + "_title");
		if (loc != null)
			return loc;
		return key;
	}

	/**
	 * Attemps to find the game localisation for a given scope
	 * 
	 * @param scope
	 *            The scope to localise
	 * @return The localisation found, plus formatting. The scope provided is
	 *         returned if no localisation is found
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
	 * 
	 * @param id
	 *            The ID of the province
	 * @return The province's name
	 */
	private static String getProvince(String id) {
		return localisation.get("prov" + id);
	}

	/**
	 * Looks up a country name
	 * 
	 * @param id
	 *            The ID of the country
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
	 * 
	 * @param type
	 *            The token type
	 * @param params
	 *            The parameters (if any) of the statement
	 * @return The formatted string. The type is returned if no formatting was
	 *         found
	 */
	public static String formatStatement(String type, String... params) {
		String statement = statements.get(type);
		return statement == null ? type : String.format(statement, (Object[]) params);
	}

	/**
	 * Gets a format string for a given token type
	 * 
	 * @param type
	 *            The token type
	 * @return The format string. The type is returned if no formatting was
	 *         found
	 */
	public static String getStatement(String type) {
		String statement = statements.get(type);
		return statement == null ? type : statement;
	}

	public static ValueType getValueType(String type, String value) {
		ValueType valType = getValueType(lookupRules.get(type));
		switch (valType) {
		case DAYS:
		case MONTHS:
		case YEARS:
		case OTHER:
			return valType;
		case PROVINCE:
			if (isCountry(value))
				return ValueType.COUNTRY;
			return valType;
		case COUNTRY:
			if (isCountry(value))
				return ValueType.COUNTRY;
			return ValueType.OTHER;
		default:
			throw new IllegalStateException("Invalid enum!");
		}
	}

	// TODO - Handle text highlighting. E.G., �Ytrade�!. Regex might be a good
	// solution.
	// TODO - Fix "has_advisor = yes/no"
}
