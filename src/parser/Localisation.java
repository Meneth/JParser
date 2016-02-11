package parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

public class Localisation {
	private static final Map<String, String> lookupRules = new HashMap<>();
	private static final Map<String, String> localisation = new HashMap<>();
	private static final Map<String, String> statements = new HashMap<>();
	private static final Set<String> regions = new HashSet<>();
	private static final Map<String, String> variations = new HashMap<>();
	private static final Map<String, String> operators = new HashMap<>();
	private static final Set<String> operatorTypes = new HashSet<>();
	
	private static final Pattern country = Pattern.compile("[a-zA-Z]{3}");
	private static final Pattern noLookup = Pattern.compile("(.* .*)|-?\\d*\\.?\\d*");
	public static final Set<String> errors = new HashSet<>();
	private static final String OPERATOR = "[OPERATOR]";

	public static enum ValueType {
		COUNTRY, PROVINCE, STATE, DAYS, MONTHS, YEARS, OTHER;
		
		public String toString() {
	        return name().toLowerCase();
	    }
	};
	
	public static enum Operator {
		LESS, NOTLESS, MORE, NOTMORE, EQUAL, NOTEQUAL;
	}

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
	public static void initialize(String path, String game) {
		try {
			Files.walk(Paths.get(String.format("statements/%s/localisation", game))).forEach(filePath -> {
				if (Files.isRegularFile(filePath)) {
					try {
						IO.readLocalisation(filePath.toString(), statements);
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			});
			for (String key : statements.keySet()) {
				if (statements.get(key).contains(OPERATOR))
					operatorTypes.add(key);
			}
			
			IO.readLookupRules(String.format("statements/%s/lookupRules.txt", game), lookupRules);
			Files.walk(Paths.get(path + "/localisation")).forEach(file -> {
				try {
					if (((Path) file).toFile().isFile())
						if (file.toString().contains("_l_english"))
							IO.readLocalisation(file.toString(), localisation);
				} catch (IOException e) {
					e.printStackTrace();
				}
			});
			if (game.equals("eu4")) {
				ParsingBlock.parseModifiers(IO.readFile(path 
						+ "/common/event_modifiers/00_event_modifiers.txt"));		
				IO.readHeaders(path + "/map/region.txt", regions, 0);
				IO.readHeaders(path + "/common/colonial_regions/00_colonial_regions.txt", regions, 0);
			}
			IO.readHeaders(path + "/map/continent.txt", regions, 0);
			Map<String, String> variationFiles = new HashMap<>();
			IO.readLocalisation(String.format("statements/%s/variations.txt", game), variationFiles);
			variationFiles.forEach((localisation, param) -> {
				try {
					if (localisation.startsWith("#"))
						return;
					String[] params = param.split(", ");
					Collection<String> vars = new HashSet<>();
					IO.readHeaders(path + params[0], vars, Integer.parseInt(params[1]));
					for (String string : vars) {
						variations.put(string, localisation);
					}
				} catch (Exception e) {
					throw new IllegalStateException(e.toString());
				}
			});
			IO.readLocalisation("statements/operators.txt", operators);
			
			if (game.equals("hoi4"))
				IO.readLocalisation("statements/hoi4/localisation/countries.txt", localisation);
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
			return formatStatement(variations.get(token.type), token.operator, findLocalisation(token.type),
					token.value);
		}
		String value = formatValue(token);

		String output = getStatement(token.type);
		if (output == null) {
			output = getScopeLocalisation(token.type, token.operator);
			if (!output.equals(token.type)) {
				return output;
			}
			errors.add(token.type);
			return token.type + ": " + value;
		}
		if (token.valueType == ValueType.COUNTRY)
			return formatStatement(token.type + "_country", token.operator, value);
		return formatStatement(token.type, token.operator, value);
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
			case STATE:
				return getState(token.value);
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
				if (isCountry(token.value))
					return getCountry(token.value);
				if (isLookup(token.value))
					return findLocalisation(token.value);
				value = token.value;
				try {
					float f = Float.parseFloat(value);
					String output = getStatement(token.type);
					if (output != null) {
						if (output.contains("%%")) {
							f *= 100;
							if (Math.abs(f) >= 1)
								value = "" + (int) f;
							else
								value = "" + String.format(Locale.US, "%.1f", f);
						}
						if (output.startsWith("%s") && f > 0)
							value = "+" + value;
					}
				} catch (NumberFormatException e) {
				}
				return value;
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
	 * Attempts to find the game localisation for a given type or value
	 * 
	 * @param key
	 *            The type or value to be localised
	 * @return The localisation found. The key provided is returned if no
	 *         localisation is found
	 */
	private static String findLocalisation(String key) {
		String key2 = key.replace("\"", "");
		String loc = getLocalisation(key2);
		if (loc != null)
			return loc;
		loc = getLocalisation("building_" + key2);
		if (loc != null)
			return loc;
		loc = getLocalisation(key2 + "_title");
		if (loc != null)
			return loc;
		return key;
	}
	
	/**
	 * Looks up a string in the "localisation" map
	 * @param key The key to the string
	 * @return The string found. Null if not found
	 */
	private static String getLocalisation(String key) {
		return localisation.get(key.toLowerCase());
	}
	
	/**
	 * Gets a format string for a given token type
	 * 
	 * @param type
	 *            The token type
	 * @return The format string. Null if not found
	 */
	public static String getStatement(String type) {
		return statements.get(type.toLowerCase());
	}

	/**
	 * Attempts to find the game localisation for a given scope
	 * 
	 * @param scope
	 *            The scope to localise
	 * @return The localisation found, plus formatting. The scope provided is
	 *         returned if no localisation is found
	 */
	private static String getScopeLocalisation(String scope, Operator operator) {
		String key2 = scope.toLowerCase();
		String loc = null;
		while (true) {
			if (regions.contains(key2))
				loc = getLocalisation(key2);
			if (loc != null)
				break;
			loc = getProvince(key2);
			if (loc != null)
				break;
			loc = getState(key2);
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
			return scope;
		}
		if (operator == Operator.NOTEQUAL)
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
		return getLocalisation("prov" + id);
	}
	
	/**
	 * Looks up a state name
	 * 
	 * @param id
	 *            The ID of the state
	 * @return The state's name
	 */
	private static String getState(String id) {
		return getLocalisation("state_" + id);
	}
	
	private enum Scope {
		ROOT, THIS, FROM, CONTROLLER, OWNER, PREV;
		
		public String toString() {
			switch (this) {
			case ROOT:
				return "our country";
			case THIS:
				return "this country";
			case FROM:
				return "from"; // TODO - Localise
			case CONTROLLER:
				return "the province's controller";
			case OWNER:
				return "the province's owner";
			case PREV:
				return "the previous scope";
			default:
				throw new IllegalArgumentException(this + " is an unlocalized enum.");
			}
		}
	}

	/**
	 * Looks up a country name
	 * 
	 * @param id
	 *            The ID of the country
	 * @return The country's name
	 */
	private static String getCountry(String id) {
		if (country.matcher(id).matches())
			return getLocalisation(id);
		return Scope.valueOf(id.toUpperCase()).toString();
	}
	
	/**
	 * Determines whether a given token value refers to a country
	 * 
	 * @param value
	 *            The token's value
	 * @return Whether it refers to a country
	 */
	private static boolean isCountry(String value) {
		try {
			Scope.valueOf(value.toUpperCase());
			return true;
		} catch (IllegalArgumentException e) {
			return country.matcher(value).matches() && !value.equals("yes");
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
	public static String formatStatement(String type, Operator operator, String... params) {
		if (operator == Operator.NOTEQUAL && !operatorTypes.contains(type))
			type += "_false";
		String statement = getStatement(type);
		if (statement == null) {
			errors.add(type);
			return statement;
		}
		if (statement.contains(OPERATOR))
			statement = insertOperator(statement, operator);
		return String.format(statement, (Object[]) params);
	}

	/**
	 * Gets a format string for a given token type
	 * 
	 * @param type
	 *            The token type
	 * @return The format string. The type is returned if no formatting was
	 *         found
	 */
	public static String fetchStatement(String type) {
		String statement = getStatement(type);
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
		case STATE:
			if (country.matcher(value).matches() && !value.equals("yes"))
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
	
	public static String getVariation(String variation) {
		return variations.get(variation);
	}
	
	private static String insertOperator(String output, Operator operator) {
		String out = output.replace(OPERATOR, operators.get(operator.toString().toLowerCase()));
		return out;
	}

	// TODO - Handle text highlighting. E.G., §Ytrade§!. Regex might be a good
	// solution.
	// TODO - Fix "has_advisor = yes/no"
	// TODO - Handle "add_manpower" having different location for |values| < 1
}
