package com.im.njams.sdk.utils;

import com.faizsiegeln.njams.messageformat.v4.command.Instruction;

public class CommonUtils {

    // SDK-276: -777 is replay ignore code
    public static boolean ignoreReplayResponseOnInstruction(Instruction instruction){
        return instruction.getResponse().getResultCode() != -777;
    }
}
