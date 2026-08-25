package com.mvpitsolutions.rooftopcamera;

import java.util.Arrays;
import net.runelite.api.gameval.ObjectID;

enum RooftopCourse
{
    DRAYNOR("Draynor Village", 12338, new int[] {
        ObjectID.ROOFTOPS_DRAYNOR_WALLCLIMB, ObjectID.ROOFTOPS_DRAYNOR_TIGHTROPE_1,
        ObjectID.ROOFTOPS_DRAYNOR_TIGHTROPE_2, ObjectID.ROOFTOPS_DRAYNOR_WALLCROSSING,
        ObjectID.ROOFTOPS_DRAYNOR_WALLSCRAMBLE, ObjectID.ROOFTOPS_DRAYNOR_LEAPDOWN,
        ObjectID.ROOFTOPS_DRAYNOR_CRATE
    }),
    AL_KHARID("Al Kharid", 13105, new int[] {
        ObjectID.ROOFTOPS_KHARID_WALLCLIMB, ObjectID.ROOFTOPS_KHARID_TIGHTROPE_1,
        ObjectID.ROOFTOPS_KHARID_ROPE_SWING, ObjectID.ROOFTOPS_KHARID_SLIDE_SIDE,
        ObjectID.ROOFTOPS_KHARID_BAMBOO_TREE_TOP, ObjectID.ROOFTOPS_KHARID_WALLCLIMB_2,
        ObjectID.ROOFTOPS_KHARID_TIGHTROPE_4, ObjectID.ROOFTOPS_KHARID_LEAPDOWN
    }),
    VARROCK("Varrock", 12853, new int[] {
        ObjectID.ROOFTOPS_VARROCK_WALLCLIMB, ObjectID.ROOFTOPS_VARROCK_CLOTHESLINE,
        ObjectID.ROOFTOPS_VARROCK_LEAPTORUINS, ObjectID.ROOFTOPS_VARROCK_WALLSWING,
        ObjectID.ROOFTOPS_VARROCK_WALLSCRAMBLE, ObjectID.ROOFTOPS_VARROCK_LEAPTOBALCONY,
        ObjectID.ROOFTOPS_VARROCK_LEAPDOWN, ObjectID.ROOFTOPS_VARROCK_STEPUPROOF,
        ObjectID.ROOFTOPS_VARROCK_FINISH
    }),
    CANIFIS("Canifis", 13878, new int[] {
        ObjectID.ROOFTOPS_CANIFIS_START_TREE, ObjectID.ROOFTOPS_CANIFIS_JUMP,
        ObjectID.ROOFTOPS_CANIFIS_JUMP_2, ObjectID.ROOFTOPS_CANIFIS_JUMP_5,
        ObjectID.ROOFTOPS_CANIFIS_JUMP_3, ObjectID.ROOFTOPS_CANIFIS_POLEVAULT,
        ObjectID.ROOFTOPS_CANIFIS_JUMP_4, ObjectID.ROOFTOPS_CANIFIS_LEAPDOWN
    }),
    FALADOR("Falador", 12084, new int[] {
        ObjectID.ROOFTOPS_FALADOR_WALLCLIMB, ObjectID.ROOFTOPS_FALADOR_TIGHTROPE_1,
        ObjectID.ROOFTOPS_FALADOR_HANDHOLDS_START, ObjectID.ROOFTOPS_FALADOR_GAP_1,
        ObjectID.ROOFTOPS_FALADOR_GAP_2, ObjectID.ROOFTOPS_FALADOR_TIGHTROPE_2,
        ObjectID.ROOFTOPS_FALADOR_TIGHTROPE_3, ObjectID.ROOFTOPS_FALADOR_GAP_3,
        ObjectID.ROOFTOPS_FALADOR_LEDGE_1, ObjectID.ROOFTOPS_FALADOR_LEDGE_2,
        ObjectID.ROOFTOPS_FALADOR_LEDGE_3A, ObjectID.ROOFTOPS_FALADOR_LEDGE_3B,
        ObjectID.ROOFTOPS_FALADOR_LEDGE_4, ObjectID.ROOFTOPS_FALADOR_EDGE
    }),
    SEERS("Seers' Village", 10806, new int[] {
        ObjectID.ROOFTOPS_SEERS_WALLCLIMB, ObjectID.ROOFTOPS_SEERS_JUMP,
        ObjectID.ROOFTOPS_SEERS_TIGHTROPE, ObjectID.ROOFTOPS_SEERS_JUMP_1,
        ObjectID.ROOFTOPS_SEERS_JUMP_2, ObjectID.ROOFTOPS_SEERS_LEAPDOWN
    }),
    POLLNIVNEACH("Pollnivneach", 13358, new int[] {
        ObjectID.ROOFTOPS_POLLNIVNEACH_BASKET, ObjectID.ROOFTOPS_POLLNIVNEACH_MARKETSTALL,
        ObjectID.ROOFTOPS_POLLNIVNEACH_HANGINGBANNER, ObjectID.ROOFTOPS_POLLNIVNEACH_GAP,
        ObjectID.ROOFTOPS_POLLNIVNEACH_TREE, ObjectID.ROOFTOPS_POLLNIVNEACH_WALLCLIMB,
        ObjectID.ROOFTOPS_POLLNIVNEACH_MONKEYBARS_START, ObjectID.ROOFTOPS_POLLNIVNEACH_TREETOP,
        ObjectID.ROOFTOPS_POLLNIVNEACH_LINE
    }),
    RELLEKKA("Rellekka", 10553, new int[] {
        ObjectID.ROOFTOPS_RELLEKKA_WALLCLIMB, ObjectID.ROOFTOPS_RELLEKKA_GAP_1,
        ObjectID.ROOFTOPS_RELLEKKA_TIGHTROPE_1, ObjectID.ROOFTOPS_RELLEKKA_GAP_2,
        ObjectID.ROOFTOPS_RELLEKKA_GAP_3, ObjectID.ROOFTOPS_RELLEKKA_TIGHTROPE_3,
        ObjectID.ROOFTOPS_RELLEKKA_DROPOFF
    }),
    ARDOUGNE("Ardougne", 10547, new int[] {
        ObjectID.ROOFTOPS_ARDY_WALLCLIMB, ObjectID.ROOFTOPS_ARDY_JUMP,
        ObjectID.ROOFTOPS_ARDY_PLANK, ObjectID.ROOFTOPS_ARDY_JUMP_2,
        ObjectID.ROOFTOPS_ARDY_JUMP_3, ObjectID.ROOFTOPS_ARDY_WALLCROSSING,
        ObjectID.ROOFTOPS_ARDY_JUMP_4
    });

    final String displayName;
    final int regionId;
    final int[] obstacles;

    RooftopCourse(String displayName, int regionId, int[] obstacles)
    {
        this.displayName = displayName;
        this.regionId = regionId;
        this.obstacles = obstacles;
    }

    boolean contains(int id)
    {
        return Arrays.stream(obstacles).anyMatch(value -> value == id);
    }

    int indexOf(int id)
    {
        for (int i = 0; i < obstacles.length; i++)
        {
            if (obstacles[i] == id)
            {
                return i;
            }
        }
        return -1;
    }

    int nextAfter(int id)
    {
        int index = indexOf(id);
        return obstacles[index < 0 ? 0 : (index + 1) % obstacles.length];
    }

    static RooftopCourse forRegion(int regionId)
    {
        return Arrays.stream(values()).filter(course -> course.regionId == regionId).findFirst().orElse(null);
    }
}
