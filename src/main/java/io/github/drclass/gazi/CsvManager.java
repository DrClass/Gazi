package io.github.drclass.gazi;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class CsvManager {
	private static final String CSV_PATH = "reminders.csv";

	public static void writeRemindersToCsv(List<Reminder> reminders) {
		try (FileWriter writer = new FileWriter(CSV_PATH)) {
			for (Reminder reminder : reminders) {
				writer.append(reminder.toString());
				writer.append("\n");
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static List<Reminder> readRemindersFromCsv() {
		if (!Files.exists(Paths.get(CSV_PATH))) {
			return null;
		}
		List<Reminder> jars = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new FileReader(CSV_PATH))) {
			String line;
			while ((line = reader.readLine()) != null) {
				Reminder reminder = Reminder.fromCsv(line);
				jars.add(reminder);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		return jars;
	}
}
