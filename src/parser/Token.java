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

public class Token {
	public final String type;
	public final String value;
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

	private static void readLocalisation(String path, String file) throws IOException {
		IO.readLocalisation(path + "/localisation/" + file + "_l_english.yml", localisation, true);
	}

	public Token(String type, boolean negative) {
		this(type, null, negative);
	}

	public Token(String type, String value, boolean negative) {
		super();
		if (value != null && value.equalsIgnoreCase("no"))
			negative = !negative;
		if (negative)
			this.type = type + "_false";
		else
			this.type = type;
		this.value = value;
	}

	public static Token tokenize(String s, boolean negative) {
		Token token;
		int index = s.indexOf('=');
		if (index == -1)
			token = new Token(s, negative);
		else
			token = new Token(s.substring(0, index).trim(), s.substring(index + 1).trim(), negative);
		return token;
	}

	public String toString() {
		return lookup(type, value);
	}

	private static String lookup(String key, String value) {
		if (variations.containsKey(key)) {
			String output = statements.get(variations.get(key));
			return String.format(output, key, value);
		}

		// Some tokens are localized differently if referring to a country
		if (COUNTRY.equals(lookupRules.get(key.replace("_false", "")))) {
			if (isCountry(value))
				key += "_country";
		}
		value = getValue(key, value);

		String output = statements.get(key);
		if (output == null) {
			output = getScopeLocalisation(key);
			if (!output.equals(key))
				return output;
			return key + ": " + value;
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
		if (output.contains("%s"))
			return String.format(output, value);
		return output;
	}

	private static boolean isBoolean(String value) {
		return value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("no");
	}

	private static String getValue(String key, String value) {
		String type;
		type = lookupRules.get(key.replace("_false", ""));
		if (key.endsWith(COUNTRY))
			type = COUNTRY;
		if (value != null) {
			if (type != null) {
				switch (type) {
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

	private static boolean isLookup(String value) {
		return !noLookup.matcher(value).matches();
	}

	private static boolean isCountry(String value) {
		return country.matcher(value).matches() || value.equalsIgnoreCase("root")
				|| value.equalsIgnoreCase("this") || value.equalsIgnoreCase("from");
	}

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

	private static String getScopeLocalisation(String key) {
		String key2 = key.replace("_false", "");
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
			errors.add(key);
			return key;
		}
		if (key.endsWith("_false"))
			loc += " - not all of the following";
		loc += ":";
		return loc;
	}

	private static String getProvince(String id) {
		return localisation.get("prov" + id);
	}

	private static String getCountry(String id) {
		switch (id) {
		case "":
			return null;
		case "ROOT":
			return "our country";
		case "THIS":
			return "this country";
		case "FROM":
			return "FROM";
		default:
			return localisation.get(id);
		}
	}

	public static String getStatement(String type) {
		String statement = statements.get(type);
		return statement == null ? type : statement;
	}

	public String getLocalisedValue() {
		return getValue(type, value);
	}
}
