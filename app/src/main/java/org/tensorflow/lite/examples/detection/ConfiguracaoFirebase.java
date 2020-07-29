package org.tensorflow.lite.examples.detection;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.List;

public class ConfiguracaoFirebase {

    private static FirebaseAuth autenticacao;
    private static DatabaseReference reference;
    private static FirebaseFirestore db = FirebaseFirestore.getInstance();

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

    public static void addItemToCollection(String collectionName, Object item, FirestoreListener listener) {
        db.collection(collectionName).add(item).addOnCompleteListener(docRef -> {
            if (docRef != null) {
                listener.onComplete();
            }
            else {
                listener.onError();
            }
        });
        // Exemplo de uso
//        Usuario usuario = new Usuario();
//        usuario.setEmail("email@qualquer.com");
//        usuario.setNome("Fulano");
//        usuario.setSenha("SomePass");
//        ConfiguracaoFirebase.addItemToCollection("pessoas", usuario, new FirestoreListener() {
//            @Override
//            public void onComplete() {
//                // Adicionou com sucesso
//            }
//
//            @Override
//            public void onError() {
//                // Deu ruim
//            }
//        });
    }

    public interface FirestoreListener {
        void onComplete();
        void onError();
    }
}
