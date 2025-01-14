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
package com.jagrosh.jmusicbot;

import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.jagrosh.jmusicbot.commands.dj.VolumeCmd;

import net.dv8tion.jda.api.entities.Member;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.VoiceChannel;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.ShutdownEvent;
import net.dv8tion.jda.api.events.guild.GuildJoinEvent;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageDeleteEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
// This one below I added
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent;
import net.dv8tion.jda.api.entities.MessageReaction;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.settings.Settings;
import net.dv8tion.jda.api.entities.TextChannel;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Role;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 *
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class Listener extends ListenerAdapter
{
    private final Bot bot;
    private final Map<String, Long> lastVolumeChange = new ConcurrentHashMap<>();
    
    public Listener(Bot bot)
    {
        this.bot = bot;
    }

    @Override
    public void onReady(ReadyEvent event) 
    {
        if(event.getJDA().getGuildCache().isEmpty())
        {
            Logger log = LoggerFactory.getLogger("MusicBot");
            log.warn("This bot is not on any guilds! Use the following link to add the bot to your guilds!");
            log.warn(event.getJDA().getInviteUrl(JMusicBot.RECOMMENDED_PERMS));
        }
        credit(event.getJDA());
        event.getJDA().getGuilds().forEach((guild) -> 
        {
            try
            {
                String defpl = bot.getSettingsManager().getSettings(guild).getDefaultPlaylist();
                VoiceChannel vc = bot.getSettingsManager().getSettings(guild).getVoiceChannel(guild);
                if(defpl!=null && vc!=null && bot.getPlayerManager().setUpHandler(guild).playFromDefault())
                {
                    guild.getAudioManager().openAudioConnection(vc);
                }
            }
            catch(Exception ignore) {}
        });
        if(bot.getConfig().useUpdateAlerts())
        {
            bot.getThreadpool().scheduleWithFixedDelay(() -> 
            {
                try
                {
                    User owner = bot.getJDA().retrieveUserById(bot.getConfig().getOwnerId()).complete();
                    String currentVersion = OtherUtil.getCurrentVersion();
                    String latestVersion = OtherUtil.getLatestVersion();
                    if(latestVersion!=null && !currentVersion.equalsIgnoreCase(latestVersion))
                    {
                        String msg = String.format(OtherUtil.NEW_VERSION_AVAILABLE, currentVersion, latestVersion);
                        owner.openPrivateChannel().queue(pc -> pc.sendMessage(msg).queue());
                    }
                }
                catch(Exception ignored) {} // ignored
            }, 0, 24, TimeUnit.HOURS);
        }
    }
    
    @Override
    public void onGuildMessageDelete(GuildMessageDeleteEvent event) 
    {
        bot.getNowplayingHandler().onMessageDelete(event.getGuild(), event.getMessageIdLong());
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event)
    {
        bot.getAloneInVoiceHandler().onVoiceUpdate(event);
    }

    @Override
    public void onShutdown(ShutdownEvent event) 
    {
        bot.shutdown();
    }

    @Override
    public void onGuildJoin(GuildJoinEvent event) 
    {
        credit(event.getJDA());
    }
    
    @Override
    public void onMessageReactionAdd(@NotNull MessageReactionAddEvent event) {
        if (event.getUser().isBot()) {
            return;
        }

        event.getChannel().retrieveMessageById(event.getMessageId()).queue(message -> {
            if (!message.getAuthor().equals(bot.getJDA().getSelfUser())) {
                return;
            }

            MessageReaction.ReactionEmote emote = event.getReaction().getReactionEmote();
            if (!emote.isEmoji()) {
                return;
            }

            String emoji = emote.getEmoji();
            User user = event.getUser();
            Guild guild = event.getGuild();

            if (!isInSameVoiceChannel(user, guild)) {
                event.getChannel().sendMessage(user.getAsMention() + " You must be in the same voice channel as the bot to use this reaction.").queue();
                return;
            }

            if (!hasPermission(user, guild)) {
                event.getChannel().sendMessage(user.getAsMention() + " You don't have permission to use this reaction. If you're trying to skip, use !skip to cast your vote.").queue();
                return;
            }

            switch (emoji) {
                case "🔊":
                    message.delete().queue();
                    handleVolumeChange(event, true);
                    break;
                case "🔉":
                    message.delete().queue();
                    handleVolumeChange(event, false);
                    break;
                case "⏭️":
                    message.delete().queue();
                    handleSkipReaction(event);
                    break;
            }
        });
    }

    private void handleVolumeChange(MessageReactionAddEvent event, boolean increase) {
        String userId = event.getUser().getId();
        long currentTime = System.currentTimeMillis();

        if (lastVolumeChange.containsKey(userId) && currentTime - lastVolumeChange.get(userId) < 2000) {
            event.getChannel().sendMessage("Please wait a moment before changing the volume again.").queue();
            return;
        }

        lastVolumeChange.put(userId, currentTime);

        if (increase) {
            VolumeCmd.volumeUp(bot, event.getGuild().getId());
        } else {
            VolumeCmd.volumeDown(bot, event.getGuild().getId());
        }

        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        if (handler != null) {
            int volume = handler.getPlayer().getVolume();
            event.getChannel().sendMessage(FormatUtil.volumeIcon(volume) + " Current volume is `" + volume + "`").queue(msg -> {
                msg.addReaction("🔉").queue();
                msg.addReaction("🔊").queue();
            });
        }

        deleteMessagesWithSameReaction(event, increase ? "🔊" : "🔉");
    }

    private void handleSkipReaction(MessageReactionAddEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        if (handler == null) {
            return;
        }

        RequestMetadata rm = handler.getRequestMetadata();
        String requestedBy = (rm != null && rm.user != null)
                ? "(requested by **" + FormatUtil.formatUsername(rm.user) + "**)"
                : "(autoplay)";

        String skipMessage = "🎶 **" + FormatUtil.formatUsername(event.getUser()).substring(0, 1).toUpperCase() 
                + FormatUtil.formatUsername(event.getUser()).substring(1) + "** skipped **"
                + handler.getPlayer().getPlayingTrack().getInfo().title + "** " + requestedBy;

        handler.getPlayer().stopTrack();
        event.getChannel().sendMessage(skipMessage).queue(msg -> {
            msg.addReaction("⏭️").queue();
        });

        deleteMessagesWithSameReaction(event, "⏭️");
    }

    private boolean isInSameVoiceChannel(User user, Guild guild) {
        Member member = guild.getMember(user);
        if (member == null) {
            return false;
        }

        VoiceChannel botChannel = guild.getSelfMember().getVoiceState().getChannel();
        VoiceChannel userChannel = member.getVoiceState().getChannel();
        return botChannel != null && botChannel.equals(userChannel);
    }

    private boolean hasPermission(User user, Guild guild) {
        Member member = guild.getMember(user);
        if (member == null) {
            return false;
        }

        if (user.getId().equals(guild.getOwnerId()) || member.hasPermission(Permission.MANAGE_SERVER)) {
            return true;
        }

        Settings settings = bot.getSettingsManager().getSettings(guild);
        Role djRole = settings.getRole(guild);
        return djRole != null && member.getRoles().contains(djRole);
    }

    private void deleteMessagesWithSameReaction(MessageReactionAddEvent event, String emoji) {
        TextChannel channel = event.getTextChannel();
        String messageId = event.getMessageId();
    
        channel.getHistory().retrievePast(5).queue(messages -> {
            boolean deletedOriginal = false;
    
            for (Message message : messages) {
                if (message.getId().equals(messageId) && !deletedOriginal) {
                    deletedOriginal = true;
                    continue;
                }
 
                if (message.getReactions().stream()
                        .anyMatch(reaction -> reaction.getReactionEmote().isEmoji() && reaction.getReactionEmote().getEmoji().equals(emoji))) {
                    message.delete().queue();
                }
            }
        });
    }    
       
    // make sure people aren't adding clones to dbots
    private void credit(JDA jda)
    {
        Guild dbots = jda.getGuildById(110373943822540800L);
        if(dbots==null)
            return;
        if(bot.getConfig().getDBots())
            return;
        jda.getTextChannelById(119222314964353025L)
                .sendMessage("This account is running JMusicBot. Please do not list bot clones on this server, <@"+bot.getConfig().getOwnerId()+">.").complete();
        dbots.leave().queue();
    }
}