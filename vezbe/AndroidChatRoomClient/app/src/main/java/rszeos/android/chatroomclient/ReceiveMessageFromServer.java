package rszeos.android.chatroomclient;

import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;

public class ReceiveMessageFromServer implements Runnable {
    MainActivity parent;
    BufferedReader br;

    public ReceiveMessageFromServer(MainActivity parent) {
        //parent ce nam trebati da bismo mogli iz ovog thread-a da menjamo sadrzaj
        //komponenti u osnovnoj aktivnosti (npr da popunjavamo Spinner sa listom
        //korisnika
        this.parent = parent;
        //BufferedReader koristimo za prijem poruka od servera, posto su sve
        //poruke u formi Stringa i linija teksta, BufferedReader je zgodniji nego
        //da citamo poruke iz InputStream objekta direktno
        this.br = parent.getBr();
    }

    @Override
    public void run() {
        //Beskonacna petlja
        while (true) {
            String line;
            try {
                /*
                   Cekaj da ti stigne linija teksta od servera. Postoje dve poruke koje nam server salje:
                1. spisak korisnika koji je uvek u formatu Users: Milan Dusan Dragan Dimitrije
                2. poruka koja nam stize od nekog drugog korisnika iz Chat room-a, koja je uvek u formatu
                    Ime korisnika koji salje poruku: Tekst poruke
                 */

                line = this.br.readLine();

                if (line.startsWith("Users: ")) {
                    /*
                    1. parsiraj pristiglu poruku,
                    2. prepoznaj korisnike koji su trenutno u Chat roomu
                    3. azuriraj ComboBox sa spiskom korisnika koji su trenutno u Chat Room-u
                    4. Prikazi prozor sa obavestenjem da je novi clan dosao/izasao iz Chat room-a (npr JOptionPane.showMessageDialog)
                     */

                    String[] imena = line.split(":")[1].trim().split(" ");

                    // VAZNO: Na osnovu podataka primljenih u ovoj niti, potrebno je azurirati
                    // GUI komponente kojima se upravlja u glavnoj UI niti. Da bi se to uradilo,
                    // koristi se metoda runOnUiThread klase MainActivity (nasledjeno od Activity)
                    // sto ce rezultovati time da se sve izmene GUI komponenti obave iz glavne
                    // UI niti kada se ona bude izvrsavala (kao da se te operacije zakazu za neki
                    // trenutak kasnije u vremenu, koji ce se naravno veoma brzo desiti, tako da se
                    // to odlaganje ni ne primecuje
                    parent.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // Brisanje trenutnog sadrzaja Spinner-a
                            parent.getUsers().setAdapter(null);
                            // Popunjavanje Spinner-a sa novim podacima u vezi sa prisutnim korisnicima
                            // Prvo uzmi referencu na spiner iz glavne aktivnosti
                            Spinner spinner = parent.getUsers();
                            // Napravi ArrayAdapter na osnovu imena korisnika prepoznatih iz poruke servera
                            // i postavi taj adapter da bude adapter zeljenog spiner-a
                            ArrayAdapter<String> adapter = new ArrayAdapter<>(parent, android.R.layout.simple_spinner_item, imena);
                            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
                            spinner.setAdapter(adapter);
                            // Na kraju, azuriraj i TextView polje iz glavne aktivnosti tako sto
                            // ces dodati novi tekst na vec postojeci. U ovom tekstu ce stajati
                            // informacije o trenutno povezanim korisnicima u ChatRoom-u
                            parent.setNewReceivedMessage("Novi clan se prikljucio ili je postojeci napustio sobu! Tretnutni clanovi su: " + line.split(": ")[1].toString());
                        }
                    });
                } else {
                    // Poruka koja je stigla je zapravo poruka koja je stigla za nas od nekog
                    // drugog korisnika. Prikazi poruku koja je primljena u polju za prijem poruka
                    parent.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            parent.setNewReceivedMessage(line);
                        }
                    });

                }
            } catch (IOException ex) {
                Toast.makeText(parent, "Ne mogu da primim poruku!", Toast.LENGTH_LONG).show();
            }
        }
    }
}
