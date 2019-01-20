package com.helen.database;

import org.jibble.pircbot.Colors;

import java.util.ArrayList;

public class Roll implements DatabaseObject {
	private static final int TRUNCATE = 10;

	final int diceSize;
	final String diceType;
	final String username;
	final ArrayList<Integer> values;

	private final boolean positive;
	boolean expand;

	Roll(String diceType, int diceSize, String username) {
		this.diceType = diceType;
		this.diceSize = diceSize;
		this.username = username;
		this.values = new ArrayList<>();
		this.positive = true;
		this.expand = true;
	}

	Roll(String diceType, int diceSize, String username, ArrayList<Integer> values, boolean positive) {
		this.diceType = diceType;
		this.diceSize = diceSize;
		this.username = username;
		this.values = values;
		this.positive = positive;
		this.expand = false;
	}

	private int getComputedRoll() {
		int sum = 0;
		for (Integer i : this.values)
			sum += i;
		if (!this.positive)
			sum *= -1;
		return sum;
	}

	public String toString() {
		int amount = this.values.size();
		StringBuilder str = new StringBuilder();
		if (!this.positive)
			str.append("-");
		str.append(amount);
		str.append("d");
		str.append(this.diceSize);
		str.append("=");
		str.append(Colors.BOLD);
		str.append(getComputedRoll());
		str.append(Colors.NORMAL);

		if (this.expand) {
			str.append(":");
			for (int i = 0; i < amount && i < TRUNCATE; i++) {
				int val = this.values.get(i);
				if (!"f".equals(this.diceType)) {
					str.append(" ");
					str.append(this.values.get(i));
				} else if (val < 0) {
					str.append(" -");
				} else if (val > 0) {
					str.append(" +");
				}
			}
			if (amount > TRUNCATE)
				str.append("…");
		}
		return str.toString();
	}

	@Override
	public String getDelimiter() {
		return Configs.getSingleProperty("dicedelim").getValue();
	}
	@Override
	public boolean displayToUser(){
		return true;
	}

}
