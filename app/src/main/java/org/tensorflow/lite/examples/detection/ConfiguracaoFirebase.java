package org.tensorflow.lite.examples.detection;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;

public class ConfiguracaoFirebase {

    private static FirebaseAuth autenticacao = FirebaseAuth.getInstance();
    private static FirebaseFirestore db = FirebaseFirestore.getInstance();

    public static FirebaseAuth getFirebaseAutenticacao() {
        return autenticacao;
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
    }

    public static void loadPeopleData(PeopleLoadListener peopleLoadListener) {
        db.collection("Pessoas").get().addOnCompleteListener(snapshotTask -> {
            if (snapshotTask.isSuccessful()) {
                ArrayList<Funcionario> funcionarios = new ArrayList<>();

                for (QueryDocumentSnapshot document : snapshotTask.getResult()) {
                    Funcionario func = document.toObject(Funcionario.class);
                    funcionarios.add(func);
                }

                peopleLoadListener.onComplete(funcionarios);
            }
        });
    }

    public interface FirestoreListener {
        void onComplete();
        void onError();
    }

    public interface PeopleLoadListener {
        void onComplete(ArrayList<Funcionario> funcionarios);
        void onError(String message);
    }
}
