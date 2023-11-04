package io.github.drclass.gazi;

public class Reminder {

	public static final int CUSTOM = 0;
	public static final int HYDRATE = 1;
	public static final int EAT = 2;

	private long userId;
	private int startOffset;
	private int reminderFrequency;
	private int reminderTotal;
	private int reminderType;
	private String reminderString;
	private boolean shutup;

	public Reminder(long userId, int startOffset, int reminderFrequency, int reminderTotal, int reminderType, String reminderString) {
		this.userId = userId;
		this.startOffset = startOffset;
		this.reminderFrequency = reminderFrequency;
		this.reminderTotal = reminderTotal;
		this.reminderType = reminderType;
		this.reminderString = reminderString;
		if (this.reminderString.equals("null")) {
			this.reminderString = "";
		}
		this.shutup = false;
	}

	public static Reminder fromCsv(String csvLine) {
		String[] values = csvLine.split(",");
		return new Reminder(Long.parseLong(values[0]), Integer.parseInt(values[1]), Integer.parseInt(values[2]), Integer.parseInt(values[3]),
				Integer.parseInt(values[4]), values[5]);
	}

	public long getUserId() {
		return userId;
	}

	public void setUserId(long userId) {
		this.userId = userId;
	}

	public int getStartOffset() {
		return startOffset;
	}

	public void setStartOffset(int startOffset) {
		this.startOffset = startOffset;
	}

	public int getReminderFrequency() {
		return reminderFrequency;
	}

	public void setReminderFrequency(int reminderFrequency) {
		this.reminderFrequency = reminderFrequency;
	}

	public int getReminderTotal() {
		return reminderTotal;
	}

	public void setReminderTotal(int reminderTotal) {
		this.reminderTotal = reminderTotal;
	}

	public int getReminderType() {
		return reminderType;
	}

	public void setReminderType(int reminderType) {
		this.reminderType = reminderType;
	}

	public String getReminderString() {
		return reminderString;
	}

	public void setReminderString(String reminderString) {
		this.reminderString = reminderString.replaceAll(",", "");
	}
	
	public boolean getShutup() {
		return shutup;
	}
	
	public void setShutup(boolean shutup) {
		this.shutup = shutup;
	}
	
	public String toString() {
		return userId + "," + startOffset + "," + reminderFrequency + "," + reminderTotal + "," + reminderType + ","
				+ (reminderString.equals("") ? "null" : reminderString);
	}
	
	public static int convertTypeStringToInteger(String type) {
		return switch (type.toUpperCase()) {
			case "HYDRATE" -> 1;
			case "EAT" -> 2;
			default -> {
				throw new IllegalArgumentException("Unexpected value: " + type);
			}
		};
	}
	
	public static String convertTypeIntegerToString(int type) {
		return switch (type) {
			case 1 -> "HYDRATE";
			case 2 -> "EAT";
			default -> {
				throw new IllegalArgumentException("Unexpected value: " + type);
			}
		};
	}
}
