package org.tensorflow.lite.examples.detection;

import java.io.InputStream;
import java.io.ObjectOutputStream;

public class TreinoTensorFlow {

    private String treino;
    private ObjectOutputStream obj;

    public TreinoTensorFlow() {
    }

    public String getTreino() {
        return treino;
    }

    public void setTreino(String treino) {
        this.treino = treino;
    }

    public ObjectOutputStream getObj() {
        return obj;
    }

    public void setObj(ObjectOutputStream obj) {
        this.obj = obj;
    }
}
