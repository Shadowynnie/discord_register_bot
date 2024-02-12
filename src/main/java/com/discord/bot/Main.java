package com.discord.bot;

public class Main
{
    public static void main(String[] args) throws InterruptedException
    {
        DiscordBot discordBot = new DiscordBot();
        discordBot.LoadConfig();
        discordBot.RunBot();
    }
}
