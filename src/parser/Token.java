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

	public static void initialize(String path) {
		try {
			IO.readLocalisation("statements/statements.txt", statements);
			IO.readLocalisation("statements/special.txt", statements);
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
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void readLocalisation(String path, String file) throws IOException {
		IO.readLocalisation(path + "/localisation/" + file + "_l_english.yml", localisation);
	}
	
	public Token(String type, boolean negative) {
		this(type, null, negative);
	}
	
	public Token(String type, String value, boolean negative) {
		super();
		type = type.toLowerCase();
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

	private String lookup(String key, String value) {
		String type;
		if (key.contains("_false"))
			type = lookupRules.get(key.replace("_false", ""));
		else
			type = lookupRules.get(key);
		boolean done = false;
		if (value != null) {
			if (type != null) {
				switch (type) {
				case "province":
					value = getProvince(value);
					done = true;
					break;
				case "country":
					if (isCountry(value)) {
						value = getCountry(value);
						key += "_country";
						done = true;
					}
					break;
				}
			}
			if (!done) {
				if (isLookup(value))
					value = getLocalisation(value);
			}
		}
		String output = statements.get(key);
		if (output == null)
			return key + ": " + value;
		if (output.contains("%s"))
			return String.format(output, value);
		return output;
	}

	private boolean isLookup(String value) {
		return !noLookup.matcher(value).matches();
	}

	private boolean isCountry(String value) {
		return country.matcher(value).matches() || value.equalsIgnoreCase("root") || value.equalsIgnoreCase("this") || value.equalsIgnoreCase("from");
	}

	private String getLocalisation(String key) {
		key = key.replace("\"", "");
		String loc = localisation.get(key);
		if (loc != null)
			return loc;
		return key;
	}

	private String getProvince(String id) {
		return getLocalisation("PROV_" + id);
	}
	
	private String getCountry(String id) {
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
}
