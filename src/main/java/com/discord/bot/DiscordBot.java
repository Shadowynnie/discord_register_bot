package com.discord.bot;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import ca.szc.configparser.Ini;

import java.sql.PreparedStatement;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.ResultSet;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import com.jcabi.aspects.Async;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.PrivateChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;

/*TODO: 1. Write change password function logic
        2. Write give GM powers function logic
        3. Make a user input UI as an embeded window like message with a user form
*  */

public class DiscordBot extends ListenerAdapter
{
    // Atributes:
    private static Path cfgPath = Paths.get("regbot.cfg");
    private static Map<String, Map<String, String>> cfgSections;
    private static JDA registerBot;
    // SETUP:---------------------------------------------------
    // Config section atributes:
    // Mysql:
    private static String mysqlHost;
    private static String mysqlAuthDB;
    private static String mysqlCharDB;
    private static String mysqlUser;
    private static String mysqlPass;
    private static int  mysqlPort;
    // Discord:-------------------------------------------------
    private static String apiKey;
    private static Long discordServerID;
    private static Long logsChannelID;
    private static Long staffRoleID;
    // SOAP: PLEASE SET SOAP IN WORLDSERVER.CONF TO ENABLED (1)
    private static String soapHost;
    private static String soapUser;
    private static String soapPass;
    private static int soapPort;
    private static int soapRBAC;
    //==================================================================================================================
    // Functions:

    // cfg file configuration loader:
    public void LoadConfig()
    {
        try
        {
            // Read from regbot.cfg:
            Ini config = new Ini().read(cfgPath);
            cfgSections = config.getSections();

            //Mysql config settings:--------------------------------------------------------
            mysqlHost = cfgSections.get("mysql").get("host");
            mysqlAuthDB = cfgSections.get("mysql").get("authdb");
            mysqlCharDB = cfgSections.get("mysql").get("chardb");
            mysqlUser = cfgSections.get("mysql").get("user");
            mysqlPass = cfgSections.get("mysql").get("pass");
            mysqlPort = Integer.parseInt(cfgSections.get("mysql").get("port"));

            //Discord config settings:------------------------------------------------------
            apiKey = cfgSections.get("discord").get("apikey");
            discordServerID = Long.parseLong(cfgSections.get("discord").get("targetserver"));
            logsChannelID = Long.parseLong(cfgSections.get("discord").get("logschannel"));
            staffRoleID = Long.parseLong(cfgSections.get("discord").get("staff"));

            //SOAP config settings:---------------------------------------------------------
            soapHost = cfgSections.get("soap").get("host");
            soapUser = cfgSections.get("soap").get("user");
            soapPass = cfgSections.get("soap").get("pass");
            soapPort = Integer.parseInt(cfgSections.get("soap").get("port"));
            soapRBAC = Integer.parseInt(cfgSections.get("soap").get("rbac"));
            //Config setting end------------------------------------------------------------

            //System.out.println(mysqlHost+ "\n" + mysqlAuthDB+ "\n" + mysqlCharDB+ "\n" + mysqlPass+ "\n" + mysqlPort+ "\n" + apiKey+ "\n" + discordServerID+ "\n" + logsChannelID+ "\n" + staffRoleID+ "\n" + soapHost+ "\n" + soapUser+ "\n" + soapPass+ "\n" + soapPort+ "\n" + soapRBAC);
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
    //SETUP END
    //==================================================================================================================
    //Build the bot:
    public void RunBot() throws InterruptedException
    {
        registerBot = JDABuilder.createDefault(apiKey)
                .addEventListeners(new DiscordBot())
                .build();

        registerBot.awaitReady();
    }
    //==================================================================================================================
    // On PM message received event:
    @Async
    @Override
    public void onMessageReceived(MessageReceivedEvent event)
    {
        // Check if the message is from a private channel:
        if (event.isFromType(ChannelType.PRIVATE))
        {
            PrivateChannel privateChannel = event.getChannel().asPrivateChannel();
            Message message = event.getMessage();
            //Check if the message author is not a bot:
            if(!message.getAuthor().isBot())
            {
                // Respond with "Message received":
                privateChannel.sendMessage("Message received from " + event.getAuthor().getName()).queue();
                System.out.println("Message received from " + event.getAuthor().getName());

                //Check if the user is the member on the discord server:
                Guild guild = event.getJDA().getGuildById(discordServerID);
                Member member = guild.retrieveMemberById(event.getAuthor().getIdLong()).complete();
                if (member != null)
                {
                    // The user is a member of the server:
                    System.out.println("The user " + message.getAuthor().getName() + " is a member of the server.");

                    //Check if the message starts with the "register", "account set password", "givemepowers":
                    //TODO: Replace this with a switch case statement with a default case
                    if (message.getContentRaw().startsWith("register"))
                    {
                        register(message);
                        return;
                    }
                    if (message.getContentRaw().startsWith("account set password"))
                    {
                        chgPasswd(message);
                        return;
                    }
                    if (message.getContentRaw().startsWith("givemepowers"))
                    {
                        givePowers(message);
                        //return;
                    }
                }
                else
                {
                    // The user is not a member of the server:
                    String logString = "[Account Management]: " + message.getAuthor() + " tried to input Command : '" + message + "' but is not in the Discord.";
                    sendMessage(logString, logsChannelID);
                    System.out.println("User is not a member of the server.");
                    privateChannel.sendMessage("You are not a member of the Discord server.").queue();
                }
            }
        }
    }
    //==================================================================================================================
    // Method to send a message to a channel:
    @Async
    private void sendMessage(String messageContent, long channelId)
    {
        TextChannel channel = registerBot.getTextChannelById(channelId);
        if (channel != null)
        {
            channel.sendMessage(messageContent).queue();
        }
    }
    //==================================================================================================================
    //Sterilize the user input, so nobody nukes the database:
    public String sterilize(String parameter)
    {
        parameter = parameter.replace("%;", ""); // Replace the character ";" with nothing. Does this really need escaping?
        parameter = parameter.replace("`", ""); // Replace the character "`" with nothing.
        parameter = parameter.replace("(%a)'", "%1`"); // For any words that end with ', replace the "'" with "`"
        parameter = parameter.replace("(%s)'(%a)", "%1;%2"); // For any words that have a space, then the character "'" and then letters, replace the "'" with a ";".
        parameter = parameter.replace("'", ""); // Replace the character "'" with nothing.
        parameter = parameter.replace("(%a)`", "%1''"); // For any words that end with "`", replace the "`" with "''"
        parameter = parameter.replace("(%s);(%a)", "%1''%2"); // For any words that have a space, then the character ";" and then letters, replace the ";" with "''".
        return parameter;
    }
    //==================================================================================================================
    // Registration function:
    @Async
    private void register(Message message)
    {
        // Check if the user entered a valid input and not just "register" word
        // to prevent index out of bounds exception when splitting the message:
        if (!message.getContentRaw().contains(" "))
        {
            String logString = ("[Register]: " + message.getAuthor() + " failed to register with following input : " + message.getContentRaw() + " <REDACTED>.");
            System.out.println(logString);
            message.getChannel().sendMessage("[Register]: There was an error processing your command.\nPlease input `register <useremail> <password>`.\nExample: `register marco@mail.com polo` would make a username marco@mail.com with password polo.").queue();
            return;
        }
        String[] messageParams = message.getContentRaw().split(" ");
        System.out.println("Registering...");
        message.getChannel().sendMessage("Registering...").queue();
        //Check if the user entered 3 parameters => register example@mail.com examplepassword:
        if(messageParams.length != 3)
        {
            String logString = ("[Register]: " + message.getAuthor() + " failed to register with following input : " + messageParams[0] + " " + messageParams[1] + " <REDACTED>.");
            System.out.println(logString);
            sendMessage(logString, logsChannelID);
            // Send a private message to the author:
            message.getChannel().sendMessage("[Register]: There was an error processing your command.\nPlease input `register <useremail> <password>`.\nExample: `register marco@mail.com polo` would make a username marco@mail.com with password polo.").queue();
            return;
        }
        // Check if the user entered a valid email:
        if(!isValidEmail(messageParams[1]))
        {
            String logString = ("[Register]: " + message.getAuthor() + " failed to register with following input : " + messageParams[0] + " " + messageParams[1] + " => invalid email adress. " + " <REDACTED>.");
            System.out.println(logString);
            sendMessage(logString, logsChannelID);
            // Send a private message to the author:
            message.getChannel().sendMessage("[Register]: There was an error processing your command.\nPlease input `register <useremail> <password>`.\nExample: `register marco@mail.com polo` would make a username marco@mail.com with password polo.").queue();
            return;
        }
        Long discordID = message.getAuthor().getIdLong();
        String bnetUserName = sterilize(messageParams[1]); //battle.net user name is a registration email adress used by servers from master branch
        String wowPassword = sterilize(messageParams[2]);
        String concat_string = String.format("bnetaccount create %s %s", bnetUserName, wowPassword);

        //===========================================SQL STUFF STARTS HERE==============================================
        // DB Connection:
        try (Connection connection = DriverManager.getConnection("jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + mysqlAuthDB, mysqlUser, mysqlPass);
             PreparedStatement statement = connection.prepareStatement("SELECT email, discord_id FROM account WHERE email = ? OR discord_id = ?"))
        {
            // Check if the email or discordID already exists in the DB:
            statement.setString(1, bnetUserName);
            statement.setLong(2, discordID);
            ResultSet result = statement.executeQuery();

            if (result.next())
            {
                if (result.getString("email").equals(bnetUserName))
                {
                    String logString = ("[Register]: " + message.getAuthor() + " failed to register because that registration email already exists in the account database.");
                    System.out.println(logString);
                    sendMessage(logString, logsChannelID);
                    message.getChannel().sendMessage("[Register]: An account with that registration email already exists.").queue();
                    return;
                } else if (result.getLong("discord_id") == discordID)
                {
                    String logString = ("[Register]: " + message.getAuthor() + " failed to register because their Discord ID already exists in the account database.");
                    System.out.println(logString);
                    sendMessage(logString, logsChannelID);
                    message.getChannel().sendMessage("[Register]: An account with that Discord ID already exists.").queue();
                    return;
                }
            }

            // =========================Perform SOAP command to create an account=======================================
            String soapUrl = "http://" + soapUser + ":" + soapPass + "@" + soapHost + ":" + soapPort + "/";
            String payLoad = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n<SOAP-ENV:Envelope xmlns:SOAP-ENV=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:ns1=\"urn:TC\">\r\n<SOAP-ENV:Body>\r\n<ns1:executeCommand>\r\n<command>" + concat_string + "</command>\r\n</ns1:executeCommand>\r\n</SOAP-ENV:Body>\r\n</SOAP-ENV:Envelope>";

            OkHttpClient client = new OkHttpClient().newBuilder()
                    .build();
            MediaType mediaType = MediaType.parse("application/xml");
            RequestBody body = RequestBody.create(mediaType, payLoad);

            Request request = new Request.Builder()
                    .url(soapUrl)
                    .method("POST", body)
                    .addHeader("Content-Type", "application/xml")
                    .addHeader("Authorization", "Basic ZGlzcmVnYm90OkppcmtueDAyKg==")
                    .build();
            Response response = client.newCall(request).execute();
            
            if (response.isSuccessful())
            {
                // Updating the existing row in the "account" table by setting its "discord_id" column to the user's Discord ID:
                try (PreparedStatement updateStatement = connection.prepareStatement("UPDATE account SET discord_id = ? WHERE email = ?"))
                {
                    updateStatement.setLong(1, discordID);
                    updateStatement.setString(2, bnetUserName);
                    updateStatement.executeUpdate();
                }

                System.out.println(response.body().string());
                String logString = "[Register]: User: " + message.getAuthor().getName() + " has registered successfully.";
                System.out.println(logString);
                sendMessage(logString, logsChannelID);
                message.getChannel().sendMessage("[Register]: Account has been successfully made.").queue();
            } else
            {
                // If the SOAP command fails:
                message.getChannel().sendMessage("[Register]: There was an error processing your command.").queue();
                System.out.println("SOAP command failed. Response code: " + response.code());
                System.out.println("SOAP response body: " + response.body().string());
            }

        } catch (SQLException | IOException | IllegalArgumentException e )
        {
            e.printStackTrace();
        }
    }

    // Change password function:
    @Async
    private void chgPasswd(Message message)
    {
        // To prevent index out of bounds exception when splitting the message:
        if (!message.getContentRaw().contains(" "))
        {
            String logString = ("[Account Management]: " + message.getAuthor() + " failed to CHANGE PASSWORD with following input : " + message.getContentRaw() + " <REDACTED>.");
            System.out.println(logString);
            message.getChannel().sendMessage("[Account Management]: There was an error processing your command.\\nPlease input `account set password <newPassword> <newPassword>`.\\nExample: `account set password 123 123` would change any password associated with your discord to '123'.").queue();
            return;
        }

        String[] messageParams = message.getContentRaw().split(" ");
        System.out.println("Changing password...");
        message.getChannel().sendMessage("Changing password...").queue();

        // Check if the user entered 4 parameters => account change password $newpassword $newpassword:
        if(messageParams.length != 4)
        {
            String logString = ("[Account Management]: " + message.getAuthor() + " failed to CHANGE PASSWORD with following input : " + messageParams[0] + " " + messageParams[1] + " <REDACTED>.");
            System.out.println(logString);
            sendMessage(logString, logsChannelID);
            // Send a private message to the author:
            message.getChannel().sendMessage("[Account Management]: There was an error processing your command.\\nPlease input `account set password <newPassword> <newPassword>`.\\nExample: `account set password 123 123` would change any password associated with your discord to '123'.").queue();
            return;
        }

        // Check if new password is equal to confirm new password
        String newPass = messageParams[3];
        String newPass_confirm = messageParams[4];
        if (!newPass.equals(newPass_confirm))
        {
            String logString = ("[Account Management]: User : \" + str(message.author) + \" failed to CHANGE PASSWORD. Variables `$newpassword` and `$newpassword` do not match.");
            System.out.println(logString);
            sendMessage(logString, logsChannelID);
        }



    }

    // Give GM powers function:
    @Async
    private void givePowers(Message message)
    {

    }

    // Email adress validity check:
    private boolean isValidEmail(String email)
    {
        String emailRegex = "^[a-zA-Z0-9_+&*-]+(?:\\.[a-zA-Z0-9_+&*-]+)*@(?:[a-zA-Z0-9-]+\\.)+[a-zA-Z]{2,7}$";
        Pattern pattern = Pattern.compile(emailRegex);
        Matcher matcher = pattern.matcher(email);
        return matcher.matches();
    }

    //==================================================================================================================
    // On ready function:
    @Async
    @Override
    public void onReady(ReadyEvent event)
    {
        System.out.println("We have logged in as " + event.getJDA().getSelfUser().getName());
    }

    //==================================================================================================================
    // Constructor:
    DiscordBot(){}
}
