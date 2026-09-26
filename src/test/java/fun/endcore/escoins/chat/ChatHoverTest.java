package fun.endcore.escoins.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatHoverTest {

    @Test
    void testHoverTokensReplacement() {
        String template = "{PREFIX}&f{PLAYER}\n" +
                "&a$ &fMoney: &a{MONEY}\n" +
                "&b⚑ &fTeam: &b{TEAM}\n" +
                "&c⚔ &fKills: &c{KILLS}\n" +
                "&6☠ &fDeaths: &6{DEATHS}\n" +
                "&e🕒 &fPlaytime: &e{PLAYTIME}\n" +
                "&bᯤ &fPing &b{PING}ms\n" +
                "\n" +
                "&e➡ &6&l&nCLICK&r &6to Message!";

        String rendered = template.replace("{PREFIX}", "&f&lMEMBER ")
                .replace("{PLAYER}", "Green_maxwell")
                .replace("{MONEY}", "785")
                .replace("{TEAM}", "Scamers")
                .replace("{KILLS}", "8")
                .replace("{DEATHS}", "21")
                .replace("{PLAYTIME}", "46h")
                .replace("{PING}", "175");

        assertTrue(rendered.contains("&f&lMEMBER &fGreen_maxwell"));
        assertTrue(rendered.contains("&a$ &fMoney: &a785"));
        assertTrue(rendered.contains("&b⚑ &fTeam: &bScamers"));
        assertTrue(rendered.contains("&c⚔ &fKills: &c8"));
        assertTrue(rendered.contains("&6☠ &fDeaths: &621"));
        assertTrue(rendered.contains("&e🕒 &fPlaytime: &e46h"));
        assertTrue(rendered.contains("&bᯤ &fPing &b175ms"));
        assertTrue(rendered.contains("&e➡ &6&l&nCLICK&r &6to Message!"));
    }

    @Test
    void testTeamFallback() {
        String team = "%betterteams_name%";
        String resolved = (team.equals("%betterteams_name%") || team.equalsIgnoreCase("none")) ? "None" : team;
        assertEquals("None", resolved);

        String realTeam = "Scamers";
        String resolvedReal = (realTeam.equals("%betterteams_name%") || realTeam.equalsIgnoreCase("none")) ? "None" : realTeam;
        assertEquals("Scamers", resolvedReal);
    }
}
