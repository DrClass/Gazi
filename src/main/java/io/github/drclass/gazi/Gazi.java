package io.github.drclass.gazi;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import discord4j.common.util.Snowflake;
import discord4j.core.DiscordClient;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.event.domain.interaction.ChatInputInteractionEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOption;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import discord4j.discordjson.json.ApplicationCommandOptionData;
import discord4j.discordjson.json.ApplicationCommandRequest;
import discord4j.rest.interaction.GuildCommandRegistrar;
import reactor.core.publisher.Mono;
import reactor.util.Logger;
import reactor.util.Loggers;

public class Gazi {
	private static final Logger log = Loggers.getLogger(Gazi.class);

	private static final long TESTING_GUILD_SNOWFLAKE = 806226208584106016L;
	private static final long MAIN_GUILD_SNOWFLAKE = 342429033809575937L;
	private static final long MAIN_GUILD_MESSAGE_CHANNEL_SNOWFLAKE = 1170407300972945568L;
	
	private static Pattern utcDigitPattern = Pattern.compile("-?\\d+");

	private static List<Reminder> reminders = null;

	private static GatewayDiscordClient client = null;

	public static void main(String[] args) {
		reminders = CsvManager.readRemindersFromCsv();
		if (reminders == null) {
			reminders = new ArrayList<Reminder>();
		}

		String token = args[0];
		client = DiscordClient.create(token).login().block();

		List<Guild> guilds = client.getGuilds().collectList().block();
		for (Guild guild : guilds) {
			if (!(guild.getId().asLong() == TESTING_GUILD_SNOWFLAKE || guild.getId().asLong() == MAIN_GUILD_SNOWFLAKE)) {
				log.warn("In an unkown guild! Leaving the guild.");
				log.warn("Guild ID: " + guild.getId().asLong());
				log.warn("Guild Name: " + guild.getName());
				guild.leave().block();
			}
		}

		List<ApplicationCommandRequest> commandList = List.of(buildReminderCommand());

		GuildCommandRegistrar.create(client.getRestClient(), commandList).registerCommands(Snowflake.of(TESTING_GUILD_SNOWFLAKE))
				.doOnError(e -> log.warn("Unable to create guild command", e)).onErrorResume(e -> Mono.empty()).blockLast();
		GuildCommandRegistrar.create(client.getRestClient(), commandList).registerCommands(Snowflake.of(MAIN_GUILD_SNOWFLAKE))
		.doOnError(e -> log.warn("Unable to create guild command", e)).onErrorResume(e -> Mono.empty()).blockLast();
		
		client.on(ChatInputInteractionEvent.class).subscribe(event -> {
			if (event.getCommandName().equals("reminder")) {
				outerSwitch:
				switch (event.getOptions().get(0).getName()) {
					case "register":
						// Extract all variables
						ApplicationCommandInteractionOption interaction = event.getOption("register").get();
						String type = interaction.getOption("type").get().getValue().get().asString();
						Matcher utcMatcher = utcDigitPattern.matcher(interaction.getOption("timezone").get().getValue().get().asString().trim());
						utcMatcher.find();
						int timezone = Integer.valueOf(utcMatcher.group());
						int startHour = Integer.valueOf(interaction.getOption("start-hour").get().getValue().get().asString().trim()) / 100;
						int startMinutes = Integer.valueOf(interaction.getOption("start-minutes").get().getValue().get().asString().trim());
						String frequencyString = interaction.getOption("frequency").get().getValue().get().asString().trim();
						int total = Integer.valueOf(interaction.getOption("total").get().getValue().get().getRaw().trim());
						// Just so happens that taking the first 2 characters always works here.
						int frequency = Integer.valueOf(frequencyString.substring(0, 2).trim());
						if (frequencyString.contains("Hour")) {
							frequency *= 60;
						}
						// Run validation to see if we would send more notifications than possible in 1 day
						if (total * frequency > 1440) {
							event.reply(
									"Error: Current settings would have notifications that extend past the 24 hour mark. Please reduce the frequency or total.")
									.withEphemeral(true).block();
							break;
						}
						// Run conversions (time zones suck)
						int startOffset = ((timezone * -60) + (startHour * 60) + startMinutes + 1440) % 1440;
						int reminderType = Reminder.convertTypeStringToInteger(type);
						// Store reminder and inform user on when they will get notified
						Reminder reminder = new Reminder(event.getInteraction().getUser().getId().asLong(), startOffset, frequency, total, reminderType, "");
						// STORE THE REMINDER DUMMY
						String output = "";
						for (int i = 0; i < reminders.size(); i++) {
							if (reminders.get(i).getUserId() == reminder.getUserId() && reminders.get(i).getReminderType() == reminder.getReminderType()) {
								reminders.remove(i);
								output += "> Replacing an already existing reminder for this type.\n";
								break;
							}
						}
						reminders.add(reminder);
						CsvManager.writeRemindersToCsv(reminders);
						output += "You will recieve notifications at the following times for every 24 hour perioid:\n";
						for (int i = 0; i < total; i++) {
							output += "<t:" + (946688400 + (startOffset * 60) + ((frequency * 60) * i)) + ":t>\n";
						}
						// DEBUG INFO
						output += "\n`DEBUG: " + type + " " + timezone + " " + startHour + " " + startMinutes + " " + frequency + " " + total + " " + startOffset + "`";
						event.reply(output).withEphemeral(true).block();
						break;
					case "remove":
						ApplicationCommandInteractionOption removeInteraction = event.getOption("remove").get();
						String removeType = removeInteraction.getOption("type").get().getValue().get().asString();
						for (int i = 0; i < reminders.size(); i++) {
							if (reminders.get(i).getUserId() == event.getInteraction().getUser().getId().asLong()
									&& reminders.get(i).getReminderType() == Reminder.convertTypeStringToInteger(removeType)) {
								reminders.remove(i);
								event.reply("Removed the reminder").withEphemeral(true).block();
								break outerSwitch;
							}
						}
						event.reply("No reminder found of that type").withEphemeral(true).block();
						break;
					case "shutup":
						ApplicationCommandInteractionOption shutupInteraction = event.getOption("remove").get();
						String shutupOutput = "";
						for (Reminder r : reminders) {
							if (r.getUserId() == event.getInteraction().getUser().getId().asLong()) {
								if (shutupInteraction.getOption("type").get().getValue().isPresent()) {
									if (Reminder.convertTypeStringToInteger(shutupInteraction.getOption("type").get().getValue().get().asString()) == r
											.getReminderType()) {
										r.setShutup(!r.getShutup());
										shutupOutput += "Reminders for " + shutupInteraction.getOption("type").get().getValue().get().asString() + " have been paused or resumed.\n";
									}
								} else {
									r.setShutup(!r.getShutup());
									shutupOutput += "Reminders for " + r.getReminderType() + " have been paused or resumed.\n";
								}
							}
						}
						if (shutupOutput.equals("")) {
							event.reply("No reminder(s) of the provided type where found").withEphemeral(true).block();
						} else {
							event.reply(shutupOutput).withEphemeral(true).block();
						}
						break;
					default:
						event.reply("Subcommand not handled yet. Please report this error.").withEphemeral(true).block();
						break;
				}
			}
		}, event -> {
			event.printStackTrace();
		});

		// Main execution loop
		long lastTick = Instant.now().getEpochSecond() - (Instant.now().getEpochSecond() % 60);
		int tickDelta = 60;
		while (true) {
			if (Instant.now().getEpochSecond() > lastTick + tickDelta) {
				lastTick += tickDelta;
				// Code goes here
				MessageChannel outputChannel = ((MessageChannel) client.getGuildById(Snowflake.of(MAIN_GUILD_SNOWFLAKE)).block()
						.getChannelById(Snowflake.of(MAIN_GUILD_MESSAGE_CHANNEL_SNOWFLAKE)).block());
				for (Reminder reminder : reminders) {
					for (int i = 0; i < reminder.getReminderTotal(); i++) {
						long temp = ((Instant.now().getEpochSecond() / 60) % 1440) - reminder.getStartOffset() - (reminder.getReminderFrequency() * i);
						if (i == 0 && temp == 0) {
							reminder.setShutup(false);
						}
						if (temp == 0) {
							outputChannel.createMessage(client.getUserById(Snowflake.of(reminder.getUserId())).block().getMention() + " make sure you "
									+ Reminder.convertTypeIntegerToString(reminder.getReminderType()).toLowerCase() + "!").subscribe();
						}
					}
				}
			}
		}
	}

	private static ApplicationCommandRequest buildReminderCommand() {
		return ApplicationCommandRequest.builder()
				.name("reminder")
				.description("Set or modify reminders")
				.addOption(ApplicationCommandOptionData.builder()
						.type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
						.name("register")
						.description("Register a new reminder or overwrite an existing one")
						.addOption(ApplicationCommandOptionData.builder()
								.type(ApplicationCommandOption.Type.STRING.getValue())
								.name("type")
								.description("Reminder Type")
								.required(true)
								.choices(List.of(
										ApplicationCommandOptionChoiceData.builder().name("Hydrate").value("Hydrate").build(),
										ApplicationCommandOptionChoiceData.builder().name("Eat").value("Eat").build()))
								.build())
						.addOption(ApplicationCommandOptionData.builder()
								.type(ApplicationCommandOption.Type.STRING.getValue())
								.name("timezone")
								.description("Your timezone")
								.required(true)
								.choices(List.of(
										ApplicationCommandOptionChoiceData.builder().name("UTC+12").value("UTC+12").build(),
										ApplicationCommandOptionChoiceData.builder().name("UTC+11").value("UTC+11").build(),
										ApplicationCommandOptionChoiceData.builder().name("UTC+10").value("UTC+10").build(),
										ApplicationCommandOptionChoiceData.builder().name("UTC+9").value("UTC+9").build(),
										ApplicationCommandOptionChoiceData.builder().name("UTC+8").value("UTC+8").build(),
										ApplicationCommandOptionChoiceData.builder().name("UTC+7").value("UTC+7").build(),
										ApplicationCommandOptionChoiceData.builder().name("UTC+6").value("UTC+6").build(),
										ApplicationCommandOptionChoiceData.builder().name("UTC+5").value("UTC+5").build(),
										ApplicationCommandOptionChoiceData.builder().name("UTC+4").value("UTC+4").build(),
										ApplicationCommandOptionChoiceData.builder().name("UTC+3").value("UTC+3").build(),
										ApplicationCommandOptionChoiceData.builder().name("UTC+2").value("UTC+2").build(),
										ApplicationCommandOptionChoiceData.builder().name("UTC+1").value("UTC+1").build(),
										ApplicationCommandOptionChoiceData.builder().name("UTC±0").value("UTC±0").build(),
										ApplicationCommandOptionChoiceData.builder().name("UTC-1").value("UTC-1").build(),
										ApplicationCommandOptionChoiceData.builder().name("UTC-2").value("UTC-2").build(),
										ApplicationCommandOptionChoiceData.builder().name("UTC-3").value("UTC-3").build(),
										ApplicationCommandOptionChoiceData.builder().name("UTC-4").value("UTC-4").build(),
										ApplicationCommandOptionChoiceData.builder().name("UTC-5").value("UTC-5").build(),
										ApplicationCommandOptionChoiceData.builder().name("UTC-6").value("UTC-6").build(),
										ApplicationCommandOptionChoiceData.builder().name("UTC-7").value("UTC-7").build(),
										ApplicationCommandOptionChoiceData.builder().name("UTC-8").value("UTC-8").build(),
										ApplicationCommandOptionChoiceData.builder().name("UTC-9").value("UTC-9").build(),
										ApplicationCommandOptionChoiceData.builder().name("UTC-10").value("UTC-10").build(),
										ApplicationCommandOptionChoiceData.builder().name("UTC-11").value("UTC-11").build(),
										ApplicationCommandOptionChoiceData.builder().name("UTC-12").value("UTC-12").build()))
								.build())
						.addOption(ApplicationCommandOptionData.builder()
								.type(ApplicationCommandOption.Type.STRING.getValue())
								.name("start-hour")
								.description("The hour you want reminders to start")
								.required(true)
								.choices(List.of(
										ApplicationCommandOptionChoiceData.builder().name("0000").value("0000").build(),
										ApplicationCommandOptionChoiceData.builder().name("0100").value("0100").build(),
										ApplicationCommandOptionChoiceData.builder().name("0200").value("0200").build(),
										ApplicationCommandOptionChoiceData.builder().name("0300").value("0300").build(),
										ApplicationCommandOptionChoiceData.builder().name("0400").value("0400").build(),
										ApplicationCommandOptionChoiceData.builder().name("0500").value("0500").build(),
										ApplicationCommandOptionChoiceData.builder().name("0600").value("0600").build(),
										ApplicationCommandOptionChoiceData.builder().name("0700").value("0700").build(),
										ApplicationCommandOptionChoiceData.builder().name("0800").value("0800").build(),
										ApplicationCommandOptionChoiceData.builder().name("0900").value("0900").build(),
										ApplicationCommandOptionChoiceData.builder().name("1000").value("1000").build(),
										ApplicationCommandOptionChoiceData.builder().name("1100").value("1100").build(),
										ApplicationCommandOptionChoiceData.builder().name("1200").value("1200").build(),
										ApplicationCommandOptionChoiceData.builder().name("1300").value("1300").build(),
										ApplicationCommandOptionChoiceData.builder().name("1400").value("1400").build(),
										ApplicationCommandOptionChoiceData.builder().name("1500").value("1500").build(),
										ApplicationCommandOptionChoiceData.builder().name("1600").value("1600").build(),
										ApplicationCommandOptionChoiceData.builder().name("1700").value("1700").build(),
										ApplicationCommandOptionChoiceData.builder().name("1800").value("1800").build(),
										ApplicationCommandOptionChoiceData.builder().name("1900").value("1900").build(),
										ApplicationCommandOptionChoiceData.builder().name("2000").value("2000").build(),
										ApplicationCommandOptionChoiceData.builder().name("2100").value("2100").build(),
										ApplicationCommandOptionChoiceData.builder().name("2200").value("2200").build(),
										ApplicationCommandOptionChoiceData.builder().name("2300").value("2300").build()))
								.build())
						.addOption(ApplicationCommandOptionData.builder()
								.type(ApplicationCommandOption.Type.STRING.getValue())
								.name("start-minutes")
								.description("The minute of the hour you want reminders to start")
								.required(true)
								.choices(List.of(
										ApplicationCommandOptionChoiceData.builder().name("00").value("00").build(),
										ApplicationCommandOptionChoiceData.builder().name("05").value("05").build(),
										ApplicationCommandOptionChoiceData.builder().name("10").value("10").build(),
										ApplicationCommandOptionChoiceData.builder().name("15").value("15").build(),
										ApplicationCommandOptionChoiceData.builder().name("20").value("20").build(),
										ApplicationCommandOptionChoiceData.builder().name("25").value("25").build(),
										ApplicationCommandOptionChoiceData.builder().name("30").value("30").build(),
										ApplicationCommandOptionChoiceData.builder().name("35").value("35").build(),
										ApplicationCommandOptionChoiceData.builder().name("40").value("40").build(),
										ApplicationCommandOptionChoiceData.builder().name("45").value("45").build(),
										ApplicationCommandOptionChoiceData.builder().name("50").value("50").build(),
										ApplicationCommandOptionChoiceData.builder().name("55").value("55").build()))
								.build())
						.addOption(ApplicationCommandOptionData.builder()
								.type(ApplicationCommandOption.Type.STRING.getValue())
								.name("frequency")
								.description("How often you want to be reminded")
								.required(true)
								.choices(List.of(
										ApplicationCommandOptionChoiceData.builder().name("15 Minutes").value("15 Minutes").build(),
										ApplicationCommandOptionChoiceData.builder().name("30 Minutes").value("30 Minutes").build(),
										ApplicationCommandOptionChoiceData.builder().name("45 Minutes").value("45 Minutes").build(),
										ApplicationCommandOptionChoiceData.builder().name("1 Hour").value("1 Hour").build(),
										ApplicationCommandOptionChoiceData.builder().name("2 Hours").value("2 Hours").build(),
										ApplicationCommandOptionChoiceData.builder().name("3 Hours").value("3 Hours").build(),
										ApplicationCommandOptionChoiceData.builder().name("4 Hours").value("4 Hours").build(),
										ApplicationCommandOptionChoiceData.builder().name("5 Hours").value("5 Hours").build(),
										ApplicationCommandOptionChoiceData.builder().name("6 Hours").value("6 Hours").build(),
										ApplicationCommandOptionChoiceData.builder().name("7 Hours").value("7 Hours").build(),
										ApplicationCommandOptionChoiceData.builder().name("8 Hours").value("8 Hours").build(),
										ApplicationCommandOptionChoiceData.builder().name("9 Hours").value("9 Hours").build(),
										ApplicationCommandOptionChoiceData.builder().name("10 Hours").value("10 Hours").build(),
										ApplicationCommandOptionChoiceData.builder().name("11 Hours").value("11 Hours").build(),
										ApplicationCommandOptionChoiceData.builder().name("12 Hours").value("12 Hours").build()))
								.build())
						.addOption(ApplicationCommandOptionData.builder()
								.type(ApplicationCommandOption.Type.INTEGER.getValue())
								.name("total")
								.description("How many reminders you want per 24 hour period")
								.required(true)
								.build())
						.build())
				.addOption(ApplicationCommandOptionData.builder()
						.type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
						.name("remove")
						.description("Remove a reminder")
						.addOption(ApplicationCommandOptionData.builder()
								.type(ApplicationCommandOption.Type.STRING.getValue())
								.name("type")
								.description("Reminder Type")
								.required(true)
								.choices(List.of(
										ApplicationCommandOptionChoiceData.builder().name("Hydrate").value("Hydrate").build(),
										ApplicationCommandOptionChoiceData.builder().name("Eat").value("Eat").build()))
								.build())
						.build())
				.addOption(ApplicationCommandOptionData.builder()
						.type(ApplicationCommandOption.Type.SUB_COMMAND.getValue())
						.name("shutup")
						.description("Pauses a reminder's notifications for the rest of the period")
						.addOption(ApplicationCommandOptionData.builder()
								.type(ApplicationCommandOption.Type.STRING.getValue())
								.name("type")
								.description("Reminder Type")
								.required(false)
								.choices(List.of(
										ApplicationCommandOptionChoiceData.builder().name("Hydrate").value("Hydrate").build(),
										ApplicationCommandOptionChoiceData.builder().name("Eat").value("Eat").build()))
								.build())
						.build())
				.build();
	}
}
