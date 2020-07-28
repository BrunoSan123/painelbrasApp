/*    */ package org.tensorflow.lite.examples.detection.OpIO;
/*    */ 
/*    */

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;

/*    */
/*    */
/*    */
/*    */
/*    */
/*    */
/*    */
/*    */

/*    */
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ 
/*    */ public class IO
/*    */ {
/*    */   public static boolean inserir(String nome, Object d)   {
/*    */     try {
/* 22 */       FileOutputStream f = new FileOutputStream(new File(nome));
/* 23 */       ObjectOutputStream o = new ObjectOutputStream(f);
/* 24 */       o.writeObject(d);
/* 25 */       o.close();
/* 26 */       f.close();

        Log.d("Chegou Aqui"," Chegou Aqui Passou ");
/* 27 */       return true;
/*    */     
/*    */     }
/* 30 */     catch (FileNotFoundException ex) {
/* 31 */       Logger.getLogger(IO.class.getName()).log(Level.SEVERE, (String)null, ex);
/*    */        Log.d("Chegou Aqui","Erro Chegou Aqui  "+ex.toString());
/* 33 */       return false;
/*    */     } catch (IOException e){

        Log.d("Chegou Aqui","Erro  Chegou Aqui"+e.toString());

    }
    return false;
/*    */   }
/*    */   
/*    */   public static Object ler(String nome) {
/*    */     try {
/* 39 */       FileInputStream fi = new FileInputStream(new File(nome));
/*    */       
/* 41 */       ObjectInputStream oi = new ObjectInputStream(fi);
/* 42 */       Object o = oi.readObject();
/* 43 */       oi.close();
/* 44 */       fi.close();
/* 45 */       return o;
/* 46 */     } catch (Exception ex) {
/* 47 */       Logger.getLogger(IO.class.getName()).log(Level.SEVERE, (String)null, ex);
/* 48 */     }  
/*    */     
/* 54 */     return null;
/*    */   }
/*    */ }


/* Location:              C:\Users\junio\Downloads\RedeAnJ\lib\RedeAJBitsSimples.jar!\OpIO\IO.class
 * Java compiler version: 8 (52.0)
 * JD-Core Version:       1.1.3
 */