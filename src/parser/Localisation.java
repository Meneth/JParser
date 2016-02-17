package parser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
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
	private static final Map<String, String> operators = new HashMap<>();
	public static final Map<String, String> variations = new HashMap<>();
	private static final Set<String> operatorTypes = new HashSet<>();
	private static final Map<String, String[]> parentExceptions = new HashMap<>();
	
	private static final Pattern country = Pattern.compile("[a-zA-Z]{3}");
	private static final Pattern number = Pattern.compile("-?\\d+\\.?\\d*");
	
	private static final String OPERATOR = "[OPERATOR]";
	
	public static final Set<String> errors = new HashSet<>();
	
	public static enum Operator {
		LESS, NOTLESS, MORE, NOTMORE, EQUAL, NOTEQUAL;
	}
	
	public static enum ValueType {
		COUNTRY, PROVINCE, STATE, DAYS, MONTHS, YEARS, OTHER;
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
			IO.readLocalisation("statements/operators.txt", operators);
			IO.readExceptions(String.format("statements/%s/parentExceptions.txt", game), parentExceptions);
			
			if (game.equals("hoi4"))
				IO.readLocalisation("statements/hoi4/countries.txt", localisation);
		
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
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	public static String localize(Token token) {
		if (variations.containsKey(token.type)) {
			return formatString(variations.get(token.type), token.operator, token.inverted,
					findLocalisation(token.type), token.value);
		}
		
		String type = token.type;
		if (getValueType(token) == ValueType.COUNTRY)
			type += "_country";
		if (isParentException(token))
			type = token.parent.type + "_" + type;
		if (hasStatement(type)) {
			return formatString(type, token.operator, token.inverted, localizeValue(token));
		} else {
			String localisation = getScopeLocalisation(token);
			if (localisation == null) {
				errors.add(token.type);
				return token.type + ": " + token.value;
			}
			return localisation;
		}
	}

	public static String localizeValue(Token token) {
		ValueType type = getValueType(token);
		switch (type) {
		case PROVINCE:
			return getPrefixed("prov", token.value);
		case STATE:
			return getPrefixed("state", token.value);
		case DAYS:
		case MONTHS:
		case YEARS:
			int val = Integer.parseInt(token.value);
			if (val == -1)
				return "the rest of the campaign";
			else if (type == ValueType.DAYS) {
				if (val < 31)
					return val + " days";
				else if (val < 365)
					return (int) ((float) val / 365 * 12) + " months";
				else
					return val / 365 + " years";
			} else 
				return val + " " + type.toString().toLowerCase();
		case COUNTRY:
		case OTHER:
			if (isCountry(token.value))
				return getCountry(token.value);
			else if (isLookup(token.value))
				return findLocalisation(token.value);
			else if (isPercentage(token))
				return toPercentage(token.value);
			else
				return token.value;
		default:
			throw new IllegalStateException("Value type not found!");
		}
	}

	/**
	 * Determines whether a given token value should be formatted as a percentage
	 * @param token The token
	 * @return Whether the token value should be formatted as a percentage
	 */
	private static boolean isPercentage(Token token) {
		String statement = getStatement(token.type);
		if (statement == null)
			return false;
		if (!number.matcher(token.value).matches())
			return false;
		return getStatement(token.type).contains("%%");
	}
	
	/**
	 * Turns a string into a percentage value
	 * @param value The value
	 * @return The value as a percentage
	 */
	private static String toPercentage(String value) {
		float f = Float.parseFloat(value);
		f *= 100;
		if (Math.abs(f) >= 1)
			return "" + (int) f;
		else
			return "" + String.format(Locale.US, "%.1f", f);
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
		if (value.contains(" "))
			return false;
		try {
			Float.parseFloat(value);
			return false;
		} catch (NumberFormatException e) {
			return true;
		}
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

	private static String getPrefixed(String prefix, String value) {
		return getLocalisation(prefix + value);
	}

	/**
	 * Attempts to find the game localisation for a given type or value
	 * 
	 * @param key
	 *            The type or value to be localised
	 * @return The localisation found. The key provided is returned if no
	 *         localisation is found
	 */
	public static String findLocalisation(String key) {
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
	 * Attempts to find the game localisation for a given scope
	 * 
	 * @param token
	 *            The token to localise
	 * @return The localisation found, plus formatting. Null is
	 *         returned if no localisation is found
	 */
	private static String getScopeLocalisation(Token token) {
		String loc = null;
		while (true) {
			//if (regions.contains(token.type))
			//	loc = getLocalisation(token.type);
			if (loc != null)
				break;
			loc = getPrefixed("prov", token.type);
			if (loc != null)
				break;
			loc = getPrefixed("state_", token.type);
			if (loc != null)
				break;
			if (isCountry(token.type)) {
				loc = getCountry(token.type);
				if (loc != null)
					break;
			}
			break;
		}
		if (loc == null) {
			return null;
		}
		if (token.inverted)
			loc += " - none of the following";
		loc += ":";
		return loc;
	}

	private static ValueType getValueType(Token token) {
		if (lookupRules.containsKey(token.type)) {
			ValueType type = ValueType.valueOf(lookupRules.get(token.type).toUpperCase());
			switch (type) {
			case COUNTRY:
				if (!isCountry(token.value))
					type = ValueType.OTHER;
				break;
			case PROVINCE:
			case STATE:
				if (isCountry(token.value))
					type = ValueType.COUNTRY;
			default:
				break;
			}
			return type;
		}
		else
			return ValueType.OTHER;
	}
	
	/**
	 * Gets a format string for a given token type
	 * 
	 * @param type
	 *            The token type
	 * @return The format string. Null if not found
	 */
	private static String getStatement(String key) {
		return statements.get(key.toLowerCase());
	}
	
	/**
	 * Determines whether localisation has been defined for a given token
	 * @param token The token
	 * @return Whether localisation is defined for it
	 */
	private static boolean hasStatement(String key) {
		return statements.containsKey(key);
	}
	
	public static String formatString(String type, Operator operator, boolean inverted,
			String... values) {
		if (inverted && !operatorTypes.contains(type))
			type += "_false";
		String statement = getStatement(type);
		if (statement == null) {
			errors.add(type);
			return statement;
		}
		if (statement.contains(OPERATOR))
			statement = insertOperator(statement, operator, inverted);
		return String.format(statement, (Object[]) values);
	}

	private static String insertOperator(String statement, Operator operator, boolean inverted) {
		if (inverted) // Opposite version is offset by one
			operator = Operator.values()[operator.ordinal() + 1];
		String out = statement.replace(OPERATOR, operators.get(operator.toString().toLowerCase()));
		return out;
	}
	
	private static boolean isParentException(Token token) {
		String[] vals = parentExceptions.get(token.parent.type);
		return vals != null && Arrays.asList(vals).contains(token.type);
	}
	
	// TODO - Handle text highlighting. E.G., §Ytrade§!. Regex might be a good
		// solution.
		// TODO - Fix "has_advisor = yes/no"
		// TODO - Handle "add_manpower" having different location for |values| < 1
}
