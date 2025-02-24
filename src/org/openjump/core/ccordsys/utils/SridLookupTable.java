package org.openjump.core.ccordsys.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.openjump.core.ccordsys.Unit;

/**
 * A class to lookup in srid.txt.
 * Can find the srid, the name and/or the unit from the code or the name
 * of a Coordinate Reference System
 */
public class SridLookupTable {

    private static final Pattern pattern = Pattern
            .compile("<([^<>]*)>\\s*;\\s*<([^<>]*)>\\s*;\\s*\\[([^\\[\\]]*)\\]");

    /** Test the class*/
    public static void main(String[] args) {
        System.out.println("4326       -> " + getSrsAndUnitFromCode("4326"));
        System.out.println("WGS 84     -> " + getSrsAndUnitFromName("WGS 84"));
        System.out.println("LAMB93     -> " + getSrsAndUnitFromCode("LAMB93"));
        System.out.println("Lambert 93 -> " + getSrsAndUnitFromName("Lambert 93"));
        System.out.println("Geoportail - Reunion -> " + getSrsAndUnitFromName("Geoportail - Reunion"));
        System.out.println("4326       -> " + getSrsNameFromCode("4326"));
        System.out.println("WGS 84     -> " + getSrsCodeFromName("WGS 84"));
        System.out.println("4326       -> " + getUnitFromCode("4326"));
        System.out.println("WGS 84     -> " + getUnitFromName("WGS 84"));
    }

    private static Scanner getScanner() {
        Scanner scanner = null;
        InputStream is = ProjUtils.class.getResourceAsStream("srid.txt");
        InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
        scanner = new Scanner(isr);
        scanner.useDelimiter("\\n");
        return scanner;
    }

    public static SRSInfo getSrsAndUnitFromCode(String code) {
        SRSInfo srsInfo = new SRSInfo();
        if (code == null || code.isEmpty() || code.equals(SRSInfo.UNDEFINED)) {
            return srsInfo;
        }
        try (Scanner scanner = getScanner()) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                Matcher m = pattern.matcher(line);
                if (m.matches()) {
                    if (m.group(1).equals(code)) {
                        srsInfo .setCode(m.group(1))
                                .setDescription(m.group(2))
                                .setUnit(Unit.find(m.group(3)));
                    }
                }
            }
            return srsInfo;
        }
    }

    public static SRSInfo getSrsAndUnitFromName(String name) {
        SRSInfo srsInfo = new SRSInfo();
        if (name == null || name.isEmpty()) {
            return srsInfo;
        }
        try (Scanner scanner = getScanner()) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                Matcher m = pattern.matcher(line);
                if (m.matches()) {
                    if (normalize(m.group(2)).equals(normalize(name))) {
                        srsInfo .setCode(m.group(1))
                                .setDescription(m.group(2))
                                .setUnit(Unit.find(m.group(3)));
                    }
                }
            }
            return srsInfo;
        }
    }

    public static SRSInfo getSrsAndUnitFromCodeOrName(String codeOrName) {
        SRSInfo srsInfo = new SRSInfo();
        if (codeOrName == null || codeOrName.isEmpty() || codeOrName.equals(SRSInfo.UNDEFINED)) {
            return srsInfo;
        }
        try (Scanner scanner = getScanner()) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine().trim();
                Matcher m = pattern.matcher(line);
                if (m.matches()) {
                    if (m.group(1).equals(codeOrName) ||
                            normalize(m.group(2)).equals(normalize(codeOrName))) {
                        srsInfo .setCode(m.group(1))
                                .setDescription(m.group(2))
                                .setUnit(Unit.find(m.group(3)));
                    }
                }
            }
            return srsInfo;
        }
    }


    public static String getSrsCodeFromName(String name) {
        SRSInfo srsInfo = getSrsAndUnitFromName(name);
        return srsInfo == null ? null : srsInfo.getCode();
    }

    public static String getSrsNameFromCode(String code) {
        SRSInfo srsInfo = getSrsAndUnitFromCode(code);
        return srsInfo == null ? null : srsInfo.getDescription();
    }

    public static Unit getUnitFromName(String name) {
        SRSInfo srsInfo = getSrsAndUnitFromName(name);
        return srsInfo == null ? null : srsInfo.getUnit();
    }

    public static Unit getUnitFromCode(String code) {
        SRSInfo srsInfo = getSrsAndUnitFromCode(code);
        return srsInfo == null ? null : srsInfo.getUnit();
    }

 
    public static String getOGCWKTFromWkidCode(String code)
        throws URISyntaxException, IOException {
    InputStream is = ProjUtils.class
            .getResourceAsStream("srid2prj.txt");
    InputStreamReader isr = new InputStreamReader(is);
    String OGCWKT = "";
    String search = "<" + code.toLowerCase() + ">";
    Scanner scanner = null;
    try {
        scanner = new Scanner(isr);
        while (scanner.hasNextLine()) {
            scanner.useDelimiter("\\n");
            String line = scanner.nextLine();
            if (line.toLowerCase().contains(search)) {
                OGCWKT = line.replaceAll(search, "");
                break;
            }
        }
    } finally {
        try {
            if (scanner != null)
                scanner.close();
        } catch (Exception e) {
            System.err.println("Exception while closing scanner "
                    + e.toString());
        }
    }
    return OGCWKT;
}
    
    /**
     * Use this function to normalize srs name and get a chance to match it
     * with the one in srid.txt
     */
    private static String normalize(String name) {
        name = Normalizer.normalize(name, Normalizer.Form.NFD); // separe base character from accent
        name = name.replaceAll("\\p{M}", ""); // remove accents
        return name.toLowerCase();
    }
}
