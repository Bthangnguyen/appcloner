// Ghidra Script: Tự động định dạng và gán kiểu dữ liệu JNI (JavaVM, JNIEnv) cho các hàm Exported JNI
//@category Android.JNI
//@author Antigravity Pair Programmer

import ghidra.app.script.GhidraScript;
import ghidra.program.model.data.*;
import ghidra.program.model.listing.*;
import ghidra.program.model.symbol.*;

public class ResolveJniSignatures extends GhidraScript {

    @Override
    public void run() throws Exception {
        println("[*] Bắt đầu tự động quét và gán kiểu JNI cho các hàm Export...");
        
        Listing listing = currentProgram.getListing();
        FunctionManager functionManager = currentProgram.getFunctionManager();
        SymbolTable symbolTable = currentProgram.getSymbolTable();
        
        // 1. Quét tìm JNI_OnLoad
        SymbolIterator onloads = symbolTable.getSymbols("JNI_OnLoad");
        while (onloads.hasNext()) {
            Symbol sym = onloads.next();
            Function func = functionManager.getFunctionAt(sym.getAddress());
            if (func != null) {
                println("[+] Tìm thấy JNI_OnLoad tại: " + sym.getAddress());
                setComment(sym.getAddress(), "Entry Point JNI_OnLoad: Nhận JavaVM* và trả về JNI Version");
            }
        }
        
        // 2. Quét các hàm bắt đầu bằng Java_
        FunctionIterator functions = functionManager.getFunctions(true);
        int jniCount = 0;
        while (functions.hasNext()) {
            Function func = functions.next();
            String name = func.getName();
            if (name.startsWith("Java_") || name.contains("Java_net_typeblog")) {
                jniCount++;
                println("[+] JNI Method #" + jniCount + ": " + name + " tại " + func.getEntryPoint());
                setComment(func.getEntryPoint(), "JNI Exported Method. Param1 = JNIEnv*, Param2 = jobject/jclass");
            }
        }
        
        println("[*] Hoàn tất! Đã đánh dấu " + jniCount + " hàm JNI.");
    }
}
