package org.tensorflow.lite.examples.detection;

import org.tensorflow.lite.examples.detection.OpIO.Recognition;

public class Funcionario {

    private String nome;
    private String endereco;
    private String telefone;
    private String CPF;
    private String setor;
    private String UID;
    private boolean entradaPermitida;
    private boolean verificarFace;
    private boolean verificarMascara;
    private boolean verificarTemperatura;
    private String recognitionData;

    public Funcionario() {
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEndereco() {
        return endereco;
    }

    public void setEndereco(String endereco) {
        this.endereco = endereco;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getCPF() {
        return CPF;
    }

    public void setCPF(String CPF) {
        this.CPF = CPF;
    }

    public String getSetor() {
        return setor;
    }

    public void setSetor(String setor) {
        this.setor = setor;
    }

    public String getUID() {
        return UID;
    }

    public void setUID(String UID) {
        this.UID = UID;
    }

    public boolean isEntradaPermitida() {
        return entradaPermitida;
    }

    public void setEntradaPermitida(boolean entradaPermitida) {
        this.entradaPermitida = entradaPermitida;
    }

    public boolean isVerificarFace() {
        return verificarFace;
    }

    public void setVerificarFace(boolean verificarFace) {
        this.verificarFace = verificarFace;
    }

    public boolean isVerificarMascara() {
        return verificarMascara;
    }

    public void setVerificarMascara(boolean verificarMascara) {
        this.verificarMascara = verificarMascara;
    }

    public boolean isVerificarTemperatura() {
        return verificarTemperatura;
    }

    public void setVerificarTemperatura(boolean verificarTemperatura) {
        this.verificarTemperatura = verificarTemperatura;
    }

    public String getRecognitionData() {
        return this.recognitionData;
    }
    public void setRecognitionData(String recognitionData) {
        this.recognitionData = recognitionData;
    }
}
