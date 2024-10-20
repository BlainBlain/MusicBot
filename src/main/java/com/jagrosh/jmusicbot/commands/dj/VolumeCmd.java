/*
 * Copyright 2016 John Grosh <john.a.grosh@gmail.com>.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot.commands.dj;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.commands.DJCommand;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import net.dv8tion.jda.api.entities.MessageReaction;
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

public class VolumeCmd extends DJCommand {
    public VolumeCmd(Bot bot) {
        super(bot);
        this.name = "volume";
        this.help = "sets or shows volume";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.arguments = "[0-150]";
    }

    @Override
    public void doCommand(CommandEvent event) {
        if (event.getArgs().isEmpty()) {
            // If no argument is provided, show current volume and add reaction buttons
            showCurrentVolume(event);
        } else {
            adjustVolume(event);
        }
    }

    private void showCurrentVolume(CommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        int volume = handler.getPlayer().getVolume();
        event.reply(FormatUtil.volumeIcon(volume) + " Current volume is `" + volume + "`",
                msg -> {
                    msg.addReaction("🔉").queue(); // Volume down reaction
                    msg.addReaction("🔊").queue(); // Volume up reaction
                });
    }

    private void adjustVolume(CommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        Settings settings = event.getClient().getSettingsFor(event.getGuild());
        int volume = handler.getPlayer().getVolume();
        
        int newVolume;
        try {
            newVolume = Integer.parseInt(event.getArgs());
        } catch (NumberFormatException e) {
            event.reply(event.getClient().getError() + " Volume must be a valid integer between 0 and 150!");
            return;
        }

        if (newVolume < 0 || newVolume > 150) {
            event.reply(event.getClient().getError() + " Volume must be a valid integer between 0 and 150!");
            return;
        }

        int oldVolume = volume;
        handler.getPlayer().setVolume(newVolume);
        settings.setVolume(newVolume);
        event.reply(FormatUtil.volumeIcon(newVolume) + " Volume changed from `" + oldVolume + "` to `" + newVolume + "`",
        msg -> {
            msg.addReaction("🔉").queue(); // Volume down reaction
            msg.addReaction("🔊").queue(); // Volume up reaction
        });
    }

    public static void volumeUp(Bot bot, String guildId) {
        AudioHandler handler = (AudioHandler) bot.getJDA().getGuildById(guildId).getAudioManager().getSendingHandler();
        int volume = handler.getPlayer().getVolume();
        int newVolume = Math.min(150, volume + 2); // Increase volume by 2
        handler.getPlayer().setVolume(newVolume);
    }

    public static void volumeDown(Bot bot, String guildId) {
        AudioHandler handler = (AudioHandler) bot.getJDA().getGuildById(guildId).getAudioManager().getSendingHandler();
        int volume = handler.getPlayer().getVolume();
        int newVolume = Math.max(0, volume - 2); // Decrease volume by 2
        handler.getPlayer().setVolume(newVolume);
    }
}

