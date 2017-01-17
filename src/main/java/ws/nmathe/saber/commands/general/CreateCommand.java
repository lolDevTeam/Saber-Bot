package ws.nmathe.saber.commands.general;

import ws.nmathe.saber.Main;
import ws.nmathe.saber.commands.Command;
import ws.nmathe.saber.core.schedule.ScheduleEntryParser;
import ws.nmathe.saber.core.schedule.ScheduleManager;
import ws.nmathe.saber.utils.*;
import net.dv8tion.jda.core.entities.TextChannel;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * CreateCommand places a new entry message on the discord schedule channel
 * a ScheduleEntry is not created until the message sent by this command is parsed by
 * the listener
 */
public class CreateCommand implements Command
{
    private String prefix = Main.getBotSettings().getCommandPrefix();
    private int maxEntries = Main.getBotSettings().getMaxEntries();
    private ScheduleManager schedManager = Main.getScheduleManager();

    @Override
    public String help(boolean brief)
    {
        String USAGE_EXTENDED = "Event entries can be initialized using the form **" + prefix +
                "create <channel> <title> <start> <end> <extra>**. Entries MUST be initialized with a title, a start " +
                "time, and an end time. If your guild has multiple scheduling channels, an argument " +
                "indicating the channel is required; if you have only one schedule channel it may be left out." +
                " Start and end times should be of form h:mm with " +
                "optional am/pm appended on the end." +
                "\n\nEntries can optionally be configured with comments, repeat, and a start date. Adding **repeat " +
                "no**/**daily**/**weekly** to " +
                "**<Optional>** will configure repeat; default behavior is no repeat. Adding **date MM/dd** to " +
                "**<Optional>** will configure the start date; default behavior is to use the current date or the " +
                "next day depending on if the current time is greater than the start time. Comments may be added by" +
                " adding **\"YOUR COMMENT\"** in **<Optional>**; any number of comments may be added in **<Optional>**." +
                "\n\nIf your title, comment, or channel includes any space characters, the phrase my be enclosed in " +
                "quotations (see examples).";

        String EXAMPLES = "Ex1. **!create \"Party in the Guild Hall\" 19:00 02:00**" +
                "\nEx2. **!create \"event_channel Reminders\" \"Sign up for Raids\" 4:00pm 4:00pm**" +
                "\nEx3. **!create \"event_channl Raids\" \"Weekly Raid Event\" 7:00pm 12:00pm repeat weekly \"Healers and tanks always in " +
                "demand.\" \"PM our raid captain with your role and level if attending.\"**";

        String USAGE_BRIEF = "**" + prefix + "create** - Generates a new event entry" +
                " and sends it to the specified schedule channel.";

        if( brief )
            return USAGE_BRIEF;
        else
            return USAGE_BRIEF + "\n\n" + USAGE_EXTENDED + "\n\n" + EXAMPLES;
    }
    @Override
    public String verify(String[] args, MessageReceivedEvent event)
    {
        int index = 0;

        if (args.length < 3)
            return "Not enough arguments";

        // check channel
        Collection<TextChannel> schedChans = GuildUtilities.getValidScheduleChannels(event.getGuild());
        if( schedChans.size() > 1 )
        {
            if (args.length < 4)
                return "Not enough arguments";

            Collection<TextChannel> chans = event.getGuild().getTextChannelsByName(args[index], false);
            if (chans.isEmpty())
                return "Schedule channel **" + args[index] + "** does not exist";

            index++;
        }

        // check title
        if( args[index].length() > 255 )
            return "Your title is too long";

        index++;

        // check start
        if( !VerifyUtilities.verifyTime( args[index] ) )
            return "Argument **" + args[index] + "** is not a valid start time";

        index++;

        // check end
        if( !VerifyUtilities.verifyTime( args[index] ) )
            return "Argument **" + args[index] + "** is not a valid end time";

        index++;

        // check remaining args
        if( args.length - 1 > index )
        {
            String[] argsRemaining = Arrays.copyOfRange(args, index+1, args.length);

            boolean dateFlag = false;

            for (String arg : argsRemaining)
            {
                if (dateFlag)
                {
                    if (!VerifyUtilities.verifyDate(arg))
                        return "Argument **" + arg + "** is not a valid date";
                    dateFlag = false;
                }
                else if (arg.equals("date"))
                {
                    dateFlag = true;
                }
            }
        }

        ArrayList<Integer> entries = schedManager.getEntriesByGuild( event.getGuild().getId() );
        if( entries != null && entries.size() >= maxEntries && maxEntries > 0)
        {
            return "Maximum amount of entries has been reached."
                    +" No more entries may be added until old entries are destroyed.";
        }

        return ""; // return valid
    }

    @Override
    public void action(String[] args, MessageReceivedEvent event)
    {
        String eTitle = "";
        LocalTime eStart = LocalTime.now().plusMinutes(1);    // initialized just in case verify failed it's duty
        LocalTime eEnd = LocalTime.MIDNIGHT;                  //
        ArrayList<String> eComments = new ArrayList<>();      //
        int repeat = 0;                                      // default is 0 (no repeat)
        LocalDate eDate = LocalDate.now();                    // initialize date using the current date
        TextChannel scheduleChan = GuildUtilities.getValidScheduleChannels(event.getGuild()).get(0);

        boolean channelFlag = false;  // true if the channel name arg has been grabbed
        boolean titleFlag = false;    // true if eTitle has been grabbed from args
        boolean startFlag = false;    // true if eStart has been grabbed from args
        boolean endFlag = false;      // true if eEnd has been grabbed from args
        boolean repeatFlag = false;   // true if a 'repeat' arg has been grabbed
        boolean dateFlag = false;

        for( String arg : args )
        {
            if(!channelFlag)
            {
                channelFlag = true;
                List<TextChannel> chans = event.getGuild().getTextChannelsByName(arg,false);
                if( chans.isEmpty() )
                {
                    titleFlag = true;
                    eTitle = arg;
                }
                else
                {
                    scheduleChan = chans.get(0);
                }
            }
            else if(!titleFlag)
            {
                titleFlag = true;
                eTitle = arg;
            }
            else if(!startFlag)
            {
                startFlag = true;
                eStart = ParsingUtilities.parseTime(ZonedDateTime.now(), arg).toLocalTime();
            }
            else if(!endFlag)
            {
                endFlag = true;
                eEnd = ParsingUtilities.parseTime(ZonedDateTime.now(), arg).toLocalTime();
            }
            else
            {
                if( repeatFlag )
                {
                    String tmp = arg.toLowerCase();
                    if( tmp.toLowerCase().equals("daily") )
                        repeat = 0b1111111;
                    else if( tmp.equals("no") || tmp.equals("none") )
                        repeat = 0;
                    else
                    {
                        if( tmp.contains("su") )
                            repeat |= 1;
                        if( tmp.contains("mo") )
                            repeat |= 1<<1;
                        if( tmp.contains("tu") )
                            repeat |= 1<<2;
                        if( tmp.contains("we") )
                            repeat |= 1<<3;
                        if( tmp.contains("th") )
                            repeat |= 1<<4;
                        if( tmp.contains("fr") )
                            repeat |= 1<<5;
                        if( tmp.contains("sa") )
                            repeat |= 1<<6;
                    }
                    repeatFlag = false;
                }
                else if( dateFlag )
                {
                    if( arg.toLowerCase().equals("today") )
                        eDate = LocalDate.now();
                    else if( arg.toLowerCase().equals("tomorrow") )
                        eDate = LocalDate.now().plusDays( 1 );
                    else if( Character.isDigit(arg.charAt(0)) )
                    {
                        eDate = eDate.withMonth(Integer.parseInt(arg.split("/")[0]));
                        eDate = eDate.withDayOfMonth(Integer.parseInt(arg.split("/")[1]));
                    }
                    dateFlag = false;
                }
                else if( arg.toLowerCase().equals("repeat") )
                {
                    repeatFlag = true;
                }
                else if( arg.toLowerCase().equals("date"))
                {
                    dateFlag = true;
                }
                else
                {
                    eComments.add(arg);
                }
            }
        }
        ZonedDateTime s = ZonedDateTime.of( eDate, eStart, ZoneId.systemDefault() );
        ZonedDateTime e = ZonedDateTime.of( eDate, eEnd, ZoneId.systemDefault() );

        if(ZonedDateTime.now().isAfter(s)) //add a day if the time has already passed
        {
            s = s.plusDays(1);
            e = e.plusDays(1);
        }
        if(s.isAfter(e))        //add a day to end if end is after start
        {
            e = e.plusDays(1);
        }

        Integer Id = schedManager.newId(null);

        // generate the event entry message
        String msg = ScheduleEntryParser.generate( eTitle, s, e, eComments, repeat, Id, scheduleChan.getId() );
        __out.printOut(this.getClass(), scheduleChan.getName());

        String finalTitle = eTitle;     //  convert to effectively
        int finalRepeat = repeat;       //  final variables
        ZonedDateTime finalS = s;       //
        ZonedDateTime finalE = e;       //

        MessageUtilities.sendMsg( msg,
                scheduleChan,
                (message)->schedManager.addEntry(finalTitle, finalS, finalE, eComments, Id, message, finalRepeat) );
    }
}
