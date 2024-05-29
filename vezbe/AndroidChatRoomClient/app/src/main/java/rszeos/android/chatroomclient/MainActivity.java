package rszeos.android.chatroomclient;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;



public class MainActivity extends AppCompatActivity {
    TextView tvOutputMessages;
    EditText etNickName;
    EditText etMessage;
    Button btnEnterRoom;
    Button btnConnect;
    Button btnSend;
    Spinner spnUsers;
    private Socket socket;
    private BufferedReader br;
    private PrintWriter pw;
//    private ReceiveMessageFromServer rmfs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvOutputMessages = (TextView) findViewById(R.id.tvOutputMessages);
        tvOutputMessages.setMovementMethod(new ScrollingMovementMethod());
        etNickName = (EditText) findViewById(R.id.etNickname);
        etNickName.setEnabled(false);
        etMessage = (EditText) findViewById(R.id.etMessage);
        etMessage.setEnabled(false);
        btnEnterRoom = (Button) findViewById(R.id.btnEnterRoom);
        btnEnterRoom.setEnabled(false);
        btnConnect = (Button) findViewById(R.id.btnConnect);
        btnSend = (Button) findViewById(R.id.btnSend);
        btnSend.setEnabled(false);
        spnUsers = (Spinner) findViewById(R.id.spinner);
        spnUsers.setEnabled(false);
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //Kreiraj novi socket (ako nije localhost, treba promeniti IP adresu
                //Vazno!!! U Androidu, sve aktivnosti u vezi sa mrezom MORAJU da se vrse u zasebnoj
                //niti. Zbog toga se u metodi connectToServer kreira nova nit koja se povezuje sa
                //serverom. Na slican nacin ce se i slati poruke kasnije serveru.
                connectToServer();

                MainActivity.this.btnEnterRoom.setEnabled(true);
                MainActivity.this.etNickName.setEnabled(true);

            }
        });
        this.btnEnterRoom.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (!MainActivity.this.etNickName.getText().toString().equals("")){
                    //posalji svoje ime serveru, kao kod kreiranja socket-a, to se mora uraditi u
                    //zasebnoj niti, pogledajte implementaciju funkcije sendMessage
                    sendMessage(MainActivity.this.etNickName.getText().toString());
                    //za prijem poruka od servera (stizace asinhrono) koristi poseban thread
                    //da bismo u novom thread-u mogli da menjamo sadrzaj komponenti (npr Combo Box-a)
                    //konstruktoru novog thread-a se prosledjuje MainActivity.this (obratite paznju da
                    //na ovom mestu u kodu ako stavimo samo this, to se odnosi na objekat
                    //View.OnClickListener klase). Obratite paznju da nema potreba praviti lokalnu
                    //promenljivu ili atribut klase MainActivity koji ce biti objekat klase
                    //ReceiveMessageFromServer, cak ni objekat klase Thread (doduse objekat klase
                    //Thread bi trebalo napraviti u slucaju da zelimo negde u kodu da cekamo da se
                    // ta nit terminira pozivom na Thread.join - ovde se to ne desava)
                    new Thread(new ReceiveMessageFromServer(MainActivity.this)).start();

                    //Dozvoli koriscenje odredjenih komponenti, a zabrani ostale
                    MainActivity.this.spnUsers.setEnabled(true);
                    MainActivity.this.etMessage.setEnabled(true);
                    MainActivity.this.btnSend.setEnabled(true);
                    MainActivity.this.etNickName.setEnabled(false);
                    MainActivity.this.btnEnterRoom.setEnabled(false);
                }
                else {
                    //azuriraj status komponenti
                    MainActivity.this.spnUsers.setEnabled(false);
                    MainActivity.this.etMessage.setEnabled(false);
                    MainActivity.this.btnSend.setEnabled(false);
                }
            }
        });
        this.btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                //proveri da li se salje poruka samom sebi, ako ne, ispisi tekst poslate poruke
                //u TextView polju, a posalji poruku odgovarajuceg formata koristeci sendMessage metodu
                if (!MainActivity.this.etNickName.getText().toString().equals("") &&
                    !MainActivity.this.etNickName.getText().toString().equals(MainActivity.this.spnUsers.getSelectedItem().toString())){
                        String porukaZaIspis = "Ja" + ": " + MainActivity.this.etMessage.getText().toString();
                        String porukaZaSlanje = MainActivity.this.spnUsers.getSelectedItem().toString() + ": " + MainActivity.this.etMessage.getText().toString();
                        MainActivity.this.setNewReceivedMessage(porukaZaIspis);
                        sendMessage(porukaZaSlanje);
                }
                else{
                    if (MainActivity.this.etNickName.getText().toString().equals(MainActivity.this.spnUsers.getSelectedItem().toString())){
                        Toast.makeText(MainActivity.this, "Ne mozete slati poruke sami sebi", Toast.LENGTH_LONG).show();
                    }
                }
            }
        });
    }
    public BufferedReader getBr(){
        return this.br;
    }
    public Spinner getUsers(){
        return this.spnUsers;
    }
    public void setNewReceivedMessage(String message){
        this.tvOutputMessages.append(message + "\n");
        //da bi se scroll-ovao tekst koji se dodaje, tako da je uvek prikazan poslednji red teksta
        //potrebno je dodati ovaj kod ispod. Takodje, obratite paznju da je u layout fajlu, kod ove
        //TextView komponente postavljen atribut android:scrollbars="vertical"
        final int scrollAmount = this.tvOutputMessages.getLayout().getLineTop(this.tvOutputMessages.getLineCount()) - this.tvOutputMessages.getHeight();
        if (scrollAmount > 0){
            this.tvOutputMessages.scrollTo(0, scrollAmount);
        }
        else{
            this.tvOutputMessages.scrollTo(0, 0);
        }
    }

    public void connectToServer(){
        // kreiranje Socketa kora da se izvrsi u zasebnoj niti. VAZNO: Obzirom na to da je
        // ova metoda metoda klase MainActivity, u njoj mozemo da pristupimo atributima socket,
        // br (BufferedReader) i pw (PrintWriter) klase MainActivity. To je izuzetno vazno jer ne
        // zelimo da svaki put kada se kreira nova nit, kreiramo novi socket za komunikaciju sa
        // serverom, ili kasnije kada saljemo poruke serveru (ili primamo poruke od servera) da
        // koristimo pogresan Socket za to
       new Thread(new Runnable() {
           @Override
           public void run() {
               if (MainActivity.this.socket == null){
                   try {
                       //loopback adresa u Androidu je 10.0.2.2 slicno kao 127.0.0.1 u dosadasnjim
                       //konzolnim/GUI Java aplikacijama
                       MainActivity.this.socket = new Socket("10.0.2.2", 6001);
                   } catch (IOException e) {
                       e.printStackTrace();
                   }
                   try {
                       MainActivity.this.br = new BufferedReader(new InputStreamReader(MainActivity.this.socket.getInputStream()));
                       MainActivity.this.pw = new PrintWriter(new OutputStreamWriter(MainActivity.this.socket.getOutputStream()), true);
                   } catch (IOException e) {
                       e.printStackTrace();
                   }
               }
           }
       }).start();
    }

    public void sendMessage(String message){
        // Slicno kao kod kreiranja Socketa, slanje poruke se mora vrsiti u zasebnoj niti, ali
        // da se pri tome koristi prethodno kreirani Socket odnosno PrintWriter baziran na njemu
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (MainActivity.this.pw != null){
                    MainActivity.this.pw.println(message);
                }
            }
        }).start();
    }
}