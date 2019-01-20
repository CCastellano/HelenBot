package com.helen.database;

import java.sql.ResultSet;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Random;
import java.util.regex.Pattern;

import com.helen.commands.IncorrectUsageException;
import org.apache.log4j.Logger;
import org.jibble.pircbot.Colors;

public class Rolls {

	private static final Logger logger = Logger.getLogger(Rolls.class);
	private static final Random rng = new Random();

	private static final NumberFormat FMT = NumberFormat.getNumberInstance(Locale.US);
	private static final Pattern DICE = Pattern.compile("\\s*([+-])\\s*");
	private static final Pattern SPACE = Pattern.compile("\\s+");
	private static final Pattern SIDES = Pattern.compile("d");

	public static String roll(String cmd, String usr) throws IncorrectUsageException {
		ArrayList<Roll> rolls = new ArrayList<>();
		int score = 0;
		String[] words = SPACE.split(DICE.matcher(cmd).replaceAll(" $1"));
		int i = 1;
		for (; i < words.length; i++) {
			try {
				String word = words[i];
				String[] info = SIDES.split(word);
				switch (info.length) {
					case 1:
						score += Integer.parseInt(word);
						break;
					case 2:
						int amount = Integer.parseInt(info[0]);
						int signum = Integer.signum(amount);
						amount = Math.abs(amount);
						if (amount > 5000)
							throw new IncorrectUsageException("Cannot throw more than 5000 dice at a time.");
						String diceType = "d";
						int min, max;
						switch (info[1]) {
							case "f":
							case "F":
								diceType = "f";
								min = -1;
								max = 1;
								break;
							case "%":
								min = 1;
								max = 100;
								break;
							default:
								min = 1;
								max = Integer.parseInt(info[1]);
						}
						if (max < min)
							throw new IncorrectUsageException(max + "is too few dice sides.");
						int sides = max - min + 1;
						ArrayList<Integer> values = new ArrayList<>();
						for (int j = 0; j < amount; j++) {
							int result = rng.nextInt(sides) + min;
							score += signum * result;
							values.add(result);
						}
						rolls.add(new Roll(diceType, sides, usr, values, signum >= 0));
						break;
					default:
						throw new IncorrectUsageException(word + " is not a valid die");
				}
			} catch (NumberFormatException e) {
				break;
			}
		}
		boolean expand = false;
		StringBuilder str = new StringBuilder();
		boolean hasMessage = i < words.length;
		for (; i < words.length; i++) {
			String word = words[i];
			if ("-e".equals(word))
				expand = true;
			else {
				str.append(words[i]);
				str.append(" ");
			}
		}
		if (hasMessage)
			str.append(": ");
		str.append(Colors.BOLD);
		str.append(FMT.format(score));
		str.append(Colors.NORMAL);

		if (!rolls.isEmpty()) {
			str.append(" (");
			boolean comma = false;
			for (Roll roll : rolls) {
				if (expand) roll.expand = true;

				if (comma) str.append(", ");
				comma = true;

				str.append(roll);
				insertRoll(roll);
			}
			str.append(")");
		}

		return str.toString();
	}

	public static void insertRoll(Roll roll) {
		if (roll.diceType.equals("d")) {
			try {
				CloseableStatement stmt = Connector.getStatement(Queries.getQuery("insertRolls"),
						new java.sql.Timestamp(System.currentTimeMillis()), roll.username.toLowerCase(), 0, "");

				ResultSet rs = stmt.execute();

				if (rs != null && rs.next()) {
					int rollId = rs.getInt("rollID");
					stmt.close();
					for (int i : roll.values) {
						Connector.getStatement(Queries.getQuery("insertRoll"), rollId, i, roll.diceSize).executeUpdate();
					}
				}
				stmt.close();
			} catch (Exception e) {
				logger.error("Exception inserting dice roll", e);
			}
		}
	}

	public static String getAverage(String size, String username) {
		String average = null;
		int diceSize;
		try {
			diceSize = Integer.parseInt(size);
		} catch (Exception e) {
			return size + " is not a valid integer";
		}
		try {
			CloseableStatement stmt = Connector.getStatement(Queries.getQuery("average"), diceSize, username.toLowerCase());

			ResultSet rs = stmt.getResultSet();

			if (rs != null && rs.next()) {
				average = "The average roll for a d" + diceSize + " for you is: " + rs.getString("average") + ".";
			}
			stmt.close();
		} catch (Exception e) {
			logger.error("There was an exception retreiving average", e);
		}

		return average;

	}

	public static ArrayList<Roll> getRolls(String username) {
		ArrayList<Roll> rolls = new ArrayList<>();
		try {
			CloseableStatement stmt = Connector.getStatement(Queries.getQuery("getRolls"),username.toLowerCase(), username.toLowerCase());
			ResultSet rs = stmt.getResultSet();

			if (rs != null) {
				HashMap<Integer, Roll> rollMap = new HashMap<>();
				while (rs.next()) {
					if (!rollMap.containsKey(rs.getInt("rollId"))) {
            rollMap.put(rs.getInt("rollId"), new Roll("d", rs.getInt("size"), rs.getString("username")));
          }
					rollMap.get(rs.getInt("rollId")).values.add(rs.getInt("value"));
				}
				for (int i : rollMap.keySet()) {
					rolls.add(rollMap.get(i));
				}

			}
			return rolls;
		} catch (Exception e) {
			logger.error("There was an exception getting rolls", e);
		}
		return null;
	}

}
