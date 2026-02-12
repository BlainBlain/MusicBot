package com.jagrosh.jmusicbot.commands.music;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jlyrics.LyricsClient;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.commands.MusicCommand;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;

public class LyricsCmd extends MusicCommand {
    private final LyricsClient client = new LyricsClient();

    public LyricsCmd(Bot bot) {
        super(bot);
        this.name = "lyrics";
        this.arguments = "[song name]";
        this.help = "shows the lyrics of a song";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.botPermissions = new Permission[]{Permission.MESSAGE_SEND, Permission.MESSAGE_EMBED_LINKS};
    }

    @Override
    public void doCommand(CommandEvent event) {
        String inputTitle = event.getArgs();
        final String title;

        if (inputTitle.isEmpty()) {
            AudioHandler sendingHandler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            if (sendingHandler != null && sendingHandler.isMusicPlaying(event.getJDA())) {
                title = sendingHandler.getPlayer().getPlayingTrack().getInfo().title;
            } else {
                event.replyError("Please provide the name of the song or make sure music is playing!");
                return;
            }
        } else {
            title = inputTitle;
        }

        event.getChannel().sendTyping().queue();
        client.getLyrics(title).thenAccept(lyrics -> {
            if (lyrics == null) {
                event.replyError("Lyrics for `" + title + "` could not be found!");
                return;
            }

            String lyricsWithLineBreaks = addLineBreaks(lyrics.getContent());

            EmbedBuilder eb = new EmbedBuilder()
                    .setAuthor(lyrics.getAuthor())
                    .setColor(event.getSelfMember().getColor())
                    .setTitle(lyrics.getTitle(), lyrics.getURL())
                    .setDescription(lyricsWithLineBreaks);

            event.reply(eb.build());
        });
    }

    private String addLineBreaks(String input) {
    // Remove double quotation marks from the input text
    input = input.replace("\"", "");

    StringBuilder output = new StringBuilder();
    int wordCount = 0;
    boolean specialCharDetected = false;
    boolean openSpecialCharDetected = false;
    boolean closeBracketDetected = false;
    boolean closeParenDetected = false;

    for (int i = 0; i < input.length(); i++) {
        char c = input.charAt(i);
        char nextChar = (i + 1 < input.length()) ? input.charAt(i + 1) : '\0'; // Next character in the string

        if (Character.isUpperCase(c) && !(openSpecialCharDetected || closeParenDetected || closeBracketDetected)) {
            if ((wordCount >= 3 || specialCharDetected) && output.length() > 0) {
                output.append("\n");
                wordCount = 0; // Reset word count for a new line
                specialCharDetected = false; // Reset special character flag
            }
        } else if (isSpecialCharacter(c)) {
            if ((c == '(' && Character.isUpperCase(nextChar)) || (c == '[' && nextChar == ']')) {
                closeParenDetected = true; // Set close parenthesis flag to ignore newline if the next character is a close parenthesis or bracket
            } else {
                openSpecialCharDetected = true; // Set open special character flag
            }
        } else if (Character.isWhitespace(c)) {
            wordCount++; // Increment word count on whitespace
        } else {
            specialCharDetected = false; // Reset special character flag
            openSpecialCharDetected = false; // Reset open special character flag
            closeBracketDetected = (c == ']'); // Set close bracket flag
        }

        output.append(c);

        // Check for conditions that break the rules and reset the flags accordingly
        if ((c == ')' && closeParenDetected) || (c == ']' && closeBracketDetected)) {
            specialCharDetected = false;
            openSpecialCharDetected = false;
            closeParenDetected = false;
            closeBracketDetected = false;
        }
    }

    // Check for newline at the end to ensure the last line has at least 3 words or special characters
    if ((wordCount >= 3 || specialCharDetected || openSpecialCharDetected || closeBracketDetected || closeParenDetected) && output.length() > 0) {
        output.append("\n");
    }

    return output.toString();
}

private boolean isSpecialCharacter(char c) {
    return c == '(' || c == '[' || c == ']';
}

}
