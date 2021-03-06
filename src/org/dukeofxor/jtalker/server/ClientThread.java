package org.dukeofxor.jtalker.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;

import org.dukeofxor.jtalker.common.message.clienttoserver.LoginClientMessage;
import org.dukeofxor.jtalker.common.message.clienttoserver.LogoutClientMessage;
import org.dukeofxor.jtalker.common.message.clienttoserver.TextClientMessage;
import org.dukeofxor.jtalker.common.message.clienttoserver.WhisperServerMessage;
import org.dukeofxor.jtalker.common.message.clienttoserver.WhoisinClientMessage;
import org.dukeofxor.jtalker.common.message.servertoclient.ClientListServerMessage;
import org.dukeofxor.jtalker.common.message.servertoclient.LoginFailedMessage;
import org.dukeofxor.jtalker.common.message.servertoclient.TextServerMessage;
import org.dukeofxor.jtalker.gui.ServerGUI;

import javafx.application.Platform;

public class ClientThread extends Thread{
  
  private Socket socket;
  private ObjectInputStream inputStream;
  private ObjectOutputStream outputStream;
  private ServerGUI gui;
  private Server server;
  private String username;
  private boolean isLoggedIn;
  private Object receivedObject;

  public ClientThread(Socket socket, ServerGUI gui, Server server) {
    this.socket = socket;
    this.gui = gui;
    this.server = server;
    
    try {
      outputStream = new ObjectOutputStream(socket.getOutputStream());
      inputStream = new ObjectInputStream(socket.getInputStream());
    } catch (IOException e) {
      e.printStackTrace();
    }
    displayGuiMessage("Connected");
  }
  
  public void run() {
    boolean running = true;
    while(running){
      try {
        receivedObject = inputStream.readObject();
      } catch (ClassNotFoundException e) {
        server.removeClient(this);
        shutdown();
        break;
      } catch (IOException e) {
        //this will throw if a client disconnects
        displayGuiMessage("Disconnected without logging out");
        server.removeClient(this);
        shutdown();
        break;
      }
      
      //The following messages from the client get handled, even if the client is not logged in
      
      //LoginMessage
      if(receivedObject.getClass().equals(LoginClientMessage.class)){
        LoginClientMessage loginMessage = (LoginClientMessage) receivedObject;
        
        String username = loginMessage.getUsername();
        boolean usernameInUse = false;
        boolean userIsBanned = false;
        
        for (ClientThread clientThread : server.getConnectedClients()) {
          if(clientThread.getUsername().equals(username)){
            usernameInUse = true;
          }
        }
        
        if(server.getBannedIPs().contains(this.getIp().getHostAddress())){
        	userIsBanned = true;
        }
        
        if(!usernameInUse && !userIsBanned){
          this.username = username;
          isLoggedIn = true;
          server.addClient(this);
          displayGuiMessage("Logged in");
          continue;
        } else if(userIsBanned){
        	writeMessage(new LoginFailedMessage("You are banned from this server"));
        	displayGuiMessage("Failed to login because user is banned from this server");
        }else if(usernameInUse){
        	writeMessage(new LoginFailedMessage("Username already in use"));
        	displayGuiMessage("Failed to login because username was already in use");
        }
      }
      
      //The following messages from the client only get handled if the client is logged in
      //If a not logged in client sends such a message, he will be disconnected
      if(isLoggedIn){
        //LogoutMessage
        if(receivedObject.getClass().equals(LogoutClientMessage.class)){
            isLoggedIn = false;
            server.removeClient(this);
            displayGuiMessage("Logged out");
            running = false;
            displayGuiMessage("Disconnected");
            continue;
          }
        
        //TextMessage
        if(receivedObject.getClass().equals(TextClientMessage.class)){
          TextClientMessage textMessage = (TextClientMessage) receivedObject;
          
          server.broadcast(new TextServerMessage(username, textMessage.getText()));
          displayGuiMessage("Sent TextMessage: " + textMessage.getText());
        }
        
        //WhisperMessage
        if(receivedObject.getClass().equals(WhisperServerMessage.class)){
        	WhisperServerMessage whisperMessage = (WhisperServerMessage) receivedObject;
        	server.whisper(whisperMessage.getUsername(), whisperMessage.getText(), this.getUsername());
        	displayGuiMessage("Sent WhisperMessage: " + whisperMessage.getText());
        }
        
        //WhoisinMessage
       if(receivedObject.getClass().equals(WhoisinClientMessage.class)){
         ArrayList<String> usernameList = new ArrayList<>();
         for (ClientThread client : server.getConnectedClients()) {
          usernameList.add(client.getUsername());
         }
         
         ClientListServerMessage clientListMessage = new ClientListServerMessage(usernameList);
         writeMessage(clientListMessage);
         displayGuiMessage("Sent WhoisinMessage");
       }
      } else {
        running = false;
        displayGuiMessage("Disconnected");
        continue;
      }
    }
    server.removeClient(this);
    shutdown();
  }
  
  public void shutdown() {
      try {
        if(inputStream != null){
        inputStream.close();
        }
        if(outputStream != null){
          outputStream.close();
        }
        if(socket != null){
          socket.close();
        }
      } catch (IOException e) {
      }
  }
  
  private void displayGuiMessage(String message){
    Platform.runLater(new Runnable() {
      
      @Override
      public void run() {
        String inetAddress = socket.getInetAddress().toString().replace("/", "");
        if(getUsername() != null){
          if(!getUsername().isEmpty()){
            gui.displayMessage(inetAddress + "][" + getUsername(), message);
          } else {
            gui.displayMessage(inetAddress, message);
          }
        } else {
          gui.displayMessage(inetAddress, message);
        }
      }
    });
  }

  public boolean isConnected() {
    if(socket.isConnected()){
      return true;
    } else {
      try {
        socket.close();
      } catch (IOException e) {
        e.printStackTrace();
      }
      return false;
    }
  }

  public void writeMessage(Object message) {
    try {
      outputStream.writeObject(message);
    } catch (IOException e) {
      displayGuiMessage("Error sending message");
    }
  }

  public boolean isLoggedIn() {
    return isLoggedIn;
  }

  public String getUsername() {
    return username;
  }

  public InetAddress getIp() {
    return socket.getInetAddress();
  }

  public void logout() {
	server.getConnectedClients().remove(this);
  }
}
