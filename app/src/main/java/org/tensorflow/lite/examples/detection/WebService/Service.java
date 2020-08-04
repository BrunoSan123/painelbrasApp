package org.tensorflow.lite.examples.detection.WebService;

import android.os.AsyncTask;

import com.google.gson.Gson;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Scanner;

// classe extendendo Asynctask
public class Service extends AsyncTask<Void,Void,Catraca> {

    //boolean para sinal
    private boolean aberto;

    public Service() {
        this.aberto=true;
    }

    //Implementação da classe catacra po meio do doInBackGround

    @Override
    protected Catraca doInBackground(Void... voids) {
        //variavel para armazenar resposta
        StringBuilder resposta = new StringBuilder();

        if(this.aberto==true){
            try{
                //Definição de url e parametros de  de envio
                URL url = new URL("http://10.10.7.1/"+this.aberto+"/json/");
                HttpURLConnection connection =(HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type","application/json");
                connection.setRequestProperty("Accept","application/json");
                connection.setDoInput(true);
                connection.setConnectTimeout(5000);
                connection.connect();

                Scanner scanner =new Scanner(url.openStream());

                //Midleware
                while (scanner.hasNext()){
                    resposta.append(scanner.hasNext());

                }

            }catch (MalformedURLException e){
                e.printStackTrace();
            }catch (IOException e){
                e.printStackTrace();
            }

        }
        //transforma a resposta em um json que vai ser armazenado na váriavel  resposta e depois ficara armazenada na classe catraca
        return  new Gson().fromJson(resposta.toString(),Catraca.class);
    }
}
