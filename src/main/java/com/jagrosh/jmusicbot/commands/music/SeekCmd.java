package com.jagrosh.jmusicbot.commands.music;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.commands.MusicCommand;
import com.jagrosh.jmusicbot.utils.TimeUtils;

public class SeekCmd extends MusicCommand {
    public SeekCmd(Bot bot) {
        super(bot);
        this.name = "seek";
        this.help = "adjusts the song position forward or backward based on the current time";
        this.arguments = "<time>";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = true;
    }

    @Override
    public void doCommand(CommandEvent event) {
        String seekTimeString = event.getArgs().trim();
        if (!seekTimeString.isEmpty()) {
            int seekTimeInSeconds = TimeUtils.parseSeekTime(seekTimeString);

            if (seekTimeInSeconds != Integer.MIN_VALUE) {
                AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();

                if (handler.getPlayer().getPlayingTrack() != null) {
                    long currentTrackPosition = handler.getPlayer().getPlayingTrack().getPosition();
                    long newTrackPosition = currentTrackPosition + (seekTimeInSeconds * 1000); // convert seconds to milliseconds
                    handler.getPlayer().getPlayingTrack().setPosition(Math.max(0, newTrackPosition)); // Ensure the position is not negative
                    event.replySuccess("Successfully adjusted position by " + TimeUtils.formatTime(Math.abs(seekTimeInSeconds)) + ".");
                } else {
                    event.replyError("No track is currently playing.");
                }
            } else {
                event.replyError("Invalid time format. Please specify the time in the format `00h00m00s` or `-00h00m00s` for backward seeking.");
            }
        } else {
            event.replyError("Invalid usage. Please specify the time to seek.");
        }
    }
}
