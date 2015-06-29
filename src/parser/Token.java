package parser;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class Token {
	public final String type;
	public final String value;
	private static final Map<String, String> lookupRules = new HashMap<>();
	private static final Map<String, String> localisation = new HashMap<>();
	private static final Map<String, String> statements = new HashMap<>();
	private static final Pattern country = Pattern.compile("[A-Z]{3}");
	private static final Pattern noLookup = Pattern.compile("(.* .*)|\\d*");
	private static final String PROVINCE = "province";
	private static final String COUNTRY = "country";
	private static final String TIME = "time";

	public static void initialize(String path) {
		try {
			IO.readLocalisation("statements/statements.txt", statements, false);
			IO.readLocalisation("statements/special.txt", statements, false);
			IO.readExceptions("statements/lookupRules.txt", lookupRules);
			readLocalisation(path, "countries");
			readLocalisation(path, "eu4");
			readLocalisation(path, "text");
			readLocalisation(path, "opinions");
			readLocalisation(path, "powers_and_ideas");
			readLocalisation(path, "decisions");
			readLocalisation(path, "modifers");
			readLocalisation(path, "muslim_dlc");
			readLocalisation(path, "Purple_Phoenix");
			readLocalisation(path, "core");
			readLocalisation(path, "missions");
			readLocalisation(path, "diplomacy");
			readLocalisation(path, "flavor_events");
			readLocalisation(path, "USA_dlc");
			readLocalisation(path, "nw2");
			readLocalisation(path, "sikh");
			readLocalisation(path, "tags_phase4");
			readLocalisation(path, "flavor_events");
			readLocalisation(path, "generic_events");
			ParsingBlock.parseModifiers(IO.readFile(path + "/common/event_modifiers/00_event_modifiers.txt"));
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
		type = type.toLowerCase();
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
			token = new Token(s.substring(0, index).trim(), s.substring(
					index + 1).trim(), negative);
		return token;
	}


	public String toString() {
		return lookup(type, value);
	}

	private static String lookup(String key, String value) {
		if ("country".equals(lookupRules.get(key.replace("_false", "")))) {
			if (isCountry(value))
				key += "_country";
		}
		value = getValue(key, value);
		
		String output = statements.get(key);
		if (output == null)
			return key + ": " + value;
		if (value != null)
			try {
	            float f = Float.parseFloat(value);
	            if (output.contains("%%"))
	            	value = "" + (int) (f * 100);
	            if (output.startsWith("%s") && f > 0)
	            	value = "+" + value;
	        } catch (NumberFormatException e) { }
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
		return country.matcher(value).matches() || value.equalsIgnoreCase("root") || value.equalsIgnoreCase("this") || value.equalsIgnoreCase("from");
	}

	private static String getLocalisation(String key) {
		key = key.replace("\"", "");
		String loc = localisation.get(key);
		if (loc != null)
			return loc;
		return key;
	}

	private static String getProvince(String id) {
		return getLocalisation("PROV_" + id);
	}
	
	private static String getCountry(String id) {
		switch (id) {
		case "":
			
			return null;

		default:
			return getLocalisation(id);
		}
	}

	public static String getStatement(String type) {
		return statements.get(type);
	}

	public String getLocalisedValue() {
		return getValue(type, value);
	}
}
