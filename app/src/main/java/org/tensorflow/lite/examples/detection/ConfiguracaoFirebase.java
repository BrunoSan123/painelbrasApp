package org.tensorflow.lite.examples.detection;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class ConfiguracaoFirebase {

    private static FirebaseAuth autenticacao;
    private static DatabaseReference reference;

    public static FirebaseAuth getFirebaseAutenticacao(){

        if (autenticacao == null){
            autenticacao = FirebaseAuth.getInstance();
        }
        return autenticacao;

    }

    public static DatabaseReference getFirebase(){
        if (reference ==null){
            reference = FirebaseDatabase.getInstance().getReference();
        }
        return getFirebase();
    }
}
