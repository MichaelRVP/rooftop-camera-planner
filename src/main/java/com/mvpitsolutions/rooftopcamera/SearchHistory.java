package com.mvpitsolutions.rooftopcamera;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

final class SearchHistory
{
    private final Map<String, CameraCandidateStats> candidates = new LinkedHashMap<>();

    CameraCandidateStats get(CameraTarget target) { return candidates.get(target.key()); }

    CameraCandidateStats getOrCreate(int yaw, int pitch, int zoom)
    {
        CameraTarget target = new CameraTarget(yaw, pitch, zoom);
        return candidates.computeIfAbsent(target.key(), ignored -> new CameraCandidateStats(yaw, pitch, zoom));
    }

    Collection<CameraCandidateStats> all() { return candidates.values(); }

    CameraCandidateStats firstEligible()
    {
        for (CameraCandidateStats candidate : candidates.values())
        {
            if (candidate.isEligible()) return candidate;
        }
        return null;
    }

    CameraCandidateStats best()
    {
        CameraCandidateStats best = null;
        for (CameraCandidateStats candidate : candidates.values())
        {
            if (candidate.isEligible() && candidate.isBetterThan(best)) best = candidate;
        }
        return best;
    }

    int testedCount()
    {
        int count = 0;
        for (CameraCandidateStats candidate : candidates.values()) if (candidate.isEligible()) count++;
        return count;
    }

    String serialize()
    {
        ArrayList<String> entries = new ArrayList<>();
        for (CameraCandidateStats c : candidates.values())
        {
            String layout = c.representativeLayout == null ? "" : Base64.getUrlEncoder().withoutPadding()
                .encodeToString(c.representativeLayout.serialize().getBytes(StandardCharsets.UTF_8));
            entries.add(c.yaw + "," + c.pitch + "," + c.zoom + "," + c.samples + ","
                + c.overlapTotal + "," + c.overlapAreaTotal + "," + c.gapTotal + ","
                + c.centerTotal + "," + c.mouseTotal + "," + layout);
        }
        return String.join("~", entries);
    }

    static SearchHistory parse(String value)
    {
        SearchHistory history = new SearchHistory();
        if (value == null || value.isEmpty()) return history;
        try
        {
            for (String entry : value.split("~"))
            {
                String[] f = entry.split(",", -1);
                if (f.length != 9 && f.length != 10) continue;
                CameraCandidateStats c = history.getOrCreate(Integer.parseInt(f[0]), Integer.parseInt(f[1]), Integer.parseInt(f[2]));
                c.samples = Integer.parseInt(f[3]);
                c.overlapTotal = Double.parseDouble(f[4]);
                int offset = f.length == 10 ? 1 : 0;
                c.overlapAreaTotal = offset == 1 ? Double.parseDouble(f[5]) : 0;
                c.gapTotal = Double.parseDouble(f[5 + offset]);
                c.centerTotal = Double.parseDouble(f[6 + offset]);
                c.mouseTotal = Double.parseDouble(f[7 + offset]);
                if (!f[8 + offset].isEmpty())
                {
                    String decoded = new String(Base64.getUrlDecoder().decode(f[8 + offset]), StandardCharsets.UTF_8);
                    c.representativeLayout = ScreenMarkerLayout.parse(decoded);
                }
            }
        }
        catch (IllegalArgumentException ignored)
        {
            return new SearchHistory();
        }
        return history;
    }
}
