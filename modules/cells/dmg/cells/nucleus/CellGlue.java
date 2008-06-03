package dmg.cells.nucleus ;
import  dmg.util.Args ;
import  java.io.*;
import  java.util.* ;
import java.util.concurrent.atomic.AtomicInteger;
import  java.lang.Class ;
import  java.lang.reflect.* ;
import  java.text.*;

import org.apache.log4j.Logger;
//
// package
//
/**
  *
  *
  * @author Patrick Fuhrmann
  * @version 0.1, 15 Feb 1998
  */
class CellGlue {

   private final String    _cellDomainName      ;
   private final Hashtable<String, CellNucleus> _cellList            = new Hashtable<String, CellNucleus>() ;
   private final Hashtable<String,List<CellEventListener>> _cellEventListener   = new Hashtable<String,List<CellEventListener>>() ;
   private final Hashtable<String, CellNucleus> _killedCellList      = new Hashtable<String, CellNucleus>() ;
   private final Hashtable<String, Object> _cellContext         = new Hashtable<String, Object>() ;
   private final AtomicInteger       _uniqueCounter       = new AtomicInteger(100) ;
   public  int       _printoutLevel       = 0 ;
   public  int       _defPrintoutLevel    = CellNucleus.PRINT_ERRORS ;
   private CellNucleus          _systemNucleus     = null ;
   private ClassLoaderProvider  _classLoader       = null ;
   private CellRoutingTable     _routingTable      = new CellRoutingTable() ;
   private ThreadGroup          _masterThreadGroup = null ;
   private ThreadGroup          _killerThreadGroup = null ;
   private CellPrinter          _defaultCellPrinter = new SystemCellPrinter() ;
   private CellPrinter          _cellPrinter        = _defaultCellPrinter ;

   private final static Logger _logMessages = Logger.getLogger("logger.org.dcache.cells.messages");

   CellGlue( String cellDomainName ){

      String cellDomainNameLocal  = cellDomainName ;

      if( ( cellDomainName == null ) || ( cellDomainName.equals("") ) )
    	  cellDomainNameLocal  = "*" ;

      if( cellDomainNameLocal.charAt( cellDomainNameLocal.length() - 1 ) == '*' ){
    	  cellDomainNameLocal =
    		  cellDomainNameLocal.substring(0,cellDomainNameLocal.length())+
             System.currentTimeMillis() ;
      }
      _cellDomainName = cellDomainNameLocal;
      _classLoader       = new ClassLoaderProvider() ;
      _masterThreadGroup = new ThreadGroup( "Master-Thread-Group" ) ;
      _killerThreadGroup = new ThreadGroup( "Killer-Thread-Group" ) ;
      new CellUrl( this ) ;
   }
   ThreadGroup getMasterThreadGroup(){return _masterThreadGroup ; }
   ThreadGroup getKillerThreadGroup(){return _killerThreadGroup ; }

   synchronized void addCell( String name , CellNucleus cell )
        throws IllegalArgumentException {

      if(  _killedCellList.get( name ) != null )
         throw new IllegalArgumentException( "Name Mismatch ( cell " + name + " exist  )" ) ;
      if(  _cellList.get( name ) != null )
         throw new IllegalArgumentException( "Name Mismatch ( cell " + name + " exist )" ) ;

      _cellList.put( name , cell ) ;

      sendToAll( new CellEvent( name , CellEvent.CELL_CREATED_EVENT ) ) ;
   }

   void setSystemNucleus( CellNucleus nucleus ){ _systemNucleus = nucleus ; }

   String [] [] getClassProviders(){ return _classLoader.getProviders() ; }

   void setClassProvider( String selection , String provider ){
       String type  = null ;
       String value = null ;
       int    pos   = provider.indexOf(':') ;
       if( pos < 0 ){
           if( provider.indexOf('/') >= 0 ){
              type  = "dir" ;
              value = provider ;
           }else if( provider.indexOf( '@' ) >= 0 ){
              type  = "cells" ;
              value = provider ;
           }else if( provider.equals( "system" ) ){
              type  = "system" ;
           }else if( provider.equals( "none" ) ){
              type  = "none" ;
           }else
              throw new
              IllegalArgumentException( "Can't determine provider type" ) ;
       }else{
           type  = provider.substring( 0 , pos ) ;
           value = provider.substring( pos+1 ) ;
       }
       if( type.equals( "dir" ) ){
          File file = new File( value ) ;
          if( ! file.isDirectory() )
             throw new
             IllegalArgumentException( "Not a directory : "+value ) ;
          _classLoader.addFileProvider( selection , new File( value ) ) ;
       }else if( type.equals( "cell" ) ){
          _classLoader.addCellProvider( selection ,
                                        _systemNucleus ,
                                        new CellPath( value ) ) ;
       }else if( type.equals( "system" ) ){
          _classLoader.addSystemProvider( selection );
       }else if( type.equals( "none" ) ){
          _classLoader.removeSystemProvider( selection );
       }else
         throw new
        IllegalArgumentException( "Provider type not supported : "+type ) ;

   }
   synchronized void export( CellNucleus cell ){

      sendToAll( new CellEvent( cell.getCellName() ,
                                CellEvent.CELL_EXPORTED_EVENT ) ) ;
   }
   private Class  _loadClass( String className ) throws ClassNotFoundException {
       return _classLoader.loadClass( className ) ;
   }
   public Class loadClass( String className ) throws ClassNotFoundException {
       return _classLoader.loadClass( className ) ;
   }
   Object  _newInstance( String className ,
                         String cellName ,
                         Object [] args  ,
                         boolean   systemOnly    )
       throws ClassNotFoundException ,
              NoSuchMethodException ,
              SecurityException ,
              InstantiationException ,
              InvocationTargetException ,
              IllegalAccessException ,
              ClassCastException                       {

      Class      newClass = null ;
      if( systemOnly )
          newClass =  Class.forName( className ) ;
      else
          newClass = _loadClass( className ) ;

      Object [] arguments = new Object[args.length+1] ;
      arguments[0] = cellName ;
      for( int i = 0 ; i < args.length ; i++ )
         arguments[i+1] = args[i] ;
      Class [] argClass  = new Class[arguments.length] ;
      for( int i = 0 ; i < arguments.length ; i++ )
          argClass[i] = arguments[i].getClass() ;

      return  newClass.getConstructor( argClass ).
                       newInstance( arguments ) ;

   }
   Object  _newInstance( String className ,
                         String cellName ,
                         String [] argsClassNames  ,
                         Object [] args  ,
                         boolean   systemOnly    )
       throws ClassNotFoundException ,
              NoSuchMethodException ,
              SecurityException ,
              InstantiationException ,
              InvocationTargetException ,
              IllegalAccessException ,
              ClassCastException                       {

      Class      newClass = null ;
      if( systemOnly )
          newClass =  Class.forName( className ) ;
      else
          newClass = _loadClass( className ) ;

      Object [] arguments = new Object[args.length+1] ;
      arguments[0] = cellName ;

      for( int i = 0 ; i < args.length ; i++ )
         arguments[i+1] = args[i] ;

      Class [] argClasses  = new Class[arguments.length] ;

      ClassLoader loader = newClass.getClassLoader() ;
      argClasses[0] = java.lang.String.class ;
      if( loader == null ){
          for( int i = 1 ; i < argClasses.length ; i++ )
             argClasses[i] = Class.forName( argsClassNames[i-1] ) ;
      }else{
          for( int i = 1 ; i < argClasses.length ; i++ )
             argClasses[i] = loader.loadClass( argsClassNames[i-1] ) ;
      }

      return  newClass.getConstructor( argClasses ).
                       newInstance( arguments ) ;

   }
   Dictionary<String, Object>       getCellContext(){ return _cellContext ;}
   Object           getCellContext( String str ){
       return _cellContext.get( str ) ;
   }
   CellDomainInfo   getCellDomainInfo(){
     CellDomainInfo info = new CellDomainInfo(_cellDomainName) ;
//     info.setCellDomainName( _cellDomainName ) ;
     return info ;
   }
   public void routeAdd( CellRoute route ){
      _routingTable.add( route ) ;
      sendToAll( new CellEvent( route , CellEvent.CELL_ROUTE_ADDED_EVENT ) ) ;
   }
   public void routeDelete( CellRoute route ){
      _routingTable.delete( route ) ;
      sendToAll( new CellEvent( route , CellEvent.CELL_ROUTE_DELETED_EVENT ) ) ;
   }
   CellRoutingTable getRoutingTable(){ return _routingTable ; }
   CellRoute [] getRoutingList(){ return _routingTable.getRoutingList() ; }
   synchronized CellTunnelInfo [] getCellTunnelInfos(){

      List<CellTunnelInfo> v = new ArrayList<CellTunnelInfo>() ;

      for( CellNucleus cellNucleus : _cellList.values() ){

         Cell c = cellNucleus.getThisCell() ;

         if( c instanceof CellTunnel ){
            v.add( ((CellTunnel)c).getCellTunnelInfo() ) ;
         }
      }

      return v.toArray( new CellTunnelInfo[v.size()] ) ;

   }
   synchronized String [] getCellNames(){
      int size      = _cellList.size() + _killedCellList.size() ;

      List<String> allCells = new ArrayList<String>(size);

      allCells.addAll(_cellList.keySet());
      allCells.addAll(_killedCellList.keySet());

      return  allCells.toArray(new String[size]);

   }

   int getUnique(){ return _uniqueCounter.incrementAndGet() ; }

   CellInfo getCellInfo( String name ){
      CellNucleus nucleus = _cellList.get( name ) ;
      if( nucleus == null ){
         nucleus = _killedCellList.get( name ) ;
         if( nucleus == null )return null ;
      }
      return nucleus._getCellInfo() ;
   }
   Thread [] getThreads( String name ){
      CellNucleus nucleus = _cellList.get( name ) ;
      if( nucleus == null ){
         nucleus = _killedCellList.get( name ) ;
         if( nucleus == null )return null ;
      }
      return nucleus.getThreads() ;
   }
   private void sendToAll( CellEvent event ){
      //
      // inform our event listener
      //

      for( List<CellEventListener>  listners: _cellEventListener.values() ){

         for( CellEventListener hallo : listners ){

            if( hallo == null ){
              say( "event distributor found NULL" ) ;
              continue ;
            }
            try{
               switch( event.getEventType() ){
                 case CellEvent.CELL_CREATED_EVENT :
                      hallo.cellCreated( event ) ;
                 break ;
                 case CellEvent.CELL_EXPORTED_EVENT :
                      hallo.cellExported( event ) ;
                 break ;
                 case CellEvent.CELL_DIED_EVENT :
                      hallo.cellDied( event ) ;
                 break ;
                 case CellEvent.CELL_ROUTE_ADDED_EVENT :
                      hallo.routeAdded( event ) ;
                 break ;
                 case CellEvent.CELL_ROUTE_DELETED_EVENT :
                      hallo.routeDeleted( event ) ;
                 break ;
               }
            }catch( Exception anye ){
              say( "Exception while sending "+event + " ex : "+anye ) ;
            }
         }

      }

   }
   private class SystemCellPrinter implements CellPrinter {
      private final DateFormat   _df  = new SimpleDateFormat("MM/dd HH:mm:ss" ) ;

      public void say( String  cellName ,
                        String domainName ,
                        String cellType ,
                        int    level ,
                        String msg ){

           PrintStream print = ( level & CellNucleus.PRINT_ERRORS ) != 0 ?
                               System.err : System.out  ;

           String type = ( level & ( CellNucleus.PRINT_NUCLEUS | CellNucleus.PRINT_ERROR_NUCLEUS ) ) == 0 ?
                         "Cell" : "CellNucleus" ;

           print.print( _df.format(new Date()) ) ;
           print.print(" ");
           print.print(type) ;
           print.print("(") ;
           print.print(cellName) ;
           print.print("@") ;
           print.print(_cellDomainName) ;
           print.print(") : ") ;
           print.println(msg) ;
      }

   }
   void loadCellPrinter( String cellPrinterName , Args args  ) throws Exception {
       if( ( cellPrinterName == null ) ||
            cellPrinterName.equals("") ||
            cellPrinterName.equals("default") ){

          _cellPrinter = _defaultCellPrinter ;
          return ;
       }
       Class []  argumentClasses = { dmg.util.Args.class , java.util.Dictionary.class  } ;
       Object [] arguments       = { args , _cellContext } ;

       Class cl = Class.forName( cellPrinterName ) ;

       Constructor constructor = cl.getConstructor( argumentClasses ) ;
       synchronized( this ){
          _cellPrinter = (CellPrinter)constructor.newInstance( arguments ) ;
       }
   }
   void say( String cellName , String cellType , int level ,  String msg ){
	   if( _cellPrinter != null ){
	    	  synchronized(_cellPrinter) {
	          _cellPrinter.say( cellName , _cellDomainName , cellType , level , msg ) ;
	      }
	   }
   }
   void setPrintoutLevel( int level ){ _printoutLevel = level ; }
   int  getPrintoutLevel(){ return _printoutLevel ; }
   int  getDefaultPrintoutLevel(){ return _defPrintoutLevel ; }
   void setPrintoutLevel( String cellName , int level ){
      try{
          if( cellName.equals("CellGlue") ){
             setPrintoutLevel(level) ;
             return ;
          }else if( cellName.equals("default") ){
             _defPrintoutLevel = level ;
             return ;
          }
          CellNucleus nucleus = _cellList.get( cellName ) ;
          if( nucleus != null )nucleus.setPrintoutLevel( level ) ;
      }catch( Exception e ){}
   }
   int getPrintoutLevel( String cellName ){
      try{
          if( cellName.equals("CellGlue") )return getPrintoutLevel() ;
          if( cellName.equals("default") )return getDefaultPrintoutLevel() ;
          CellNucleus nucleus =  _cellList.get( cellName ) ;
          if( nucleus != null )return nucleus.getPrintoutLevel() ;
      }catch( Exception e ){ }
      return -1 ;
   }
   void say( String str ){
      if( ( _printoutLevel & CellNucleus.PRINT_NUCLEUS ) != 0 )
      say( "Gluon" , "Gluon" , CellNucleus.PRINT_NUCLEUS , str ) ;
      /*
         System.out.println( "Gluon@"+_cellDomainName+" : "+str ) ;
      */
      return ;
   }

   void esay( String str ){
      if( ( _printoutLevel & CellNucleus.PRINT_NUCLEUS ) != 0 )
      say( "Gluon" , "Gluon" , CellNucleus.PRINT_NUCLEUS , str ) ;
      /*
         System.out.println( "Gluon@"+_cellDomainName+" : "+str ) ;
      */
      return ;
   }
   String getCellDomainName(){  return _cellDomainName ; }
   void   kill( CellNucleus nucleus ){
      _kill( nucleus , nucleus , 0 ) ;
   }
   void   kill( CellNucleus sender , String cellName )
          throws IllegalArgumentException {
      CellNucleus nucleus =  _cellList.get( cellName ) ;
      if(  nucleus == null )
         throw new IllegalArgumentException( "Cell Not Found : "+cellName  ) ;
      _kill( sender , nucleus , 0 ) ;

   }

    /**
     * Returns a named cell. This method also returns cells that have
     * been ḱilled, but which are not dead yet.
     *
     * @param cellName the name of the cell
     * @return The cell with the given name or null if there is no such
     * cell.
     */
    CellNucleus getCell(String cellName)
    {
        CellNucleus nucleus = (CellNucleus)_cellList.get(cellName);
        if (nucleus == null) {
            nucleus = (CellNucleus)_killedCellList.get(cellName);
        }
        return nucleus;
    }

    /**
     * Blocks until the given cell is dead.
     *
     * @param cellName the name of the cell
     * @param timeout the time to wait in milliseconds. A timeout
     *                of 0 means to wait forever.
     * @throws InterruptedException if another thread interrupted the
     *         current thread before or while the current thread was
     *         waiting for a notification. The interrupted status of
     *         the current thread is cleared when this exception is
     *         thrown.
     * @return True if the cell died, false in case of a timeout.
     */
    synchronized boolean join(String cellName, long timeout) throws InterruptedException
    {
        if (timeout == 0) {
            while (getCell(cellName) != null) {
                wait();
            }
            return true;
        } else {
            while (getCell(cellName) != null && timeout > 0) {
                long time = System.currentTimeMillis();
                wait(timeout);
                timeout = timeout - (System.currentTimeMillis() - time);
            }
            return (timeout > 0);
        }
    }

   synchronized void destroy( CellNucleus nucleus ){
       String name = nucleus.getCellName() ;
       _killedCellList.remove( name ) ;
       say( "destroy : sendToAll : killed"+name ) ;
       notifyAll();
//
//        CELL_DIED_EVENT moved to _kill. Otherwise
//        we have bouncing message because the WELL_KNOWN_ROUTE
//        is still there but the entry in the ps list is not.
//
//       sendToAll( new CellEvent( name , CellEvent.CELL_DIED_EVENT ) ) ;
       return ;
   }
   private synchronized void _kill( CellNucleus source ,
                                    CellNucleus destination ,
                                    long to ) {

       _cellEventListener.remove( destination.getCellName() ) ;

       CellPath    sourceAddr  = new CellPath( source.getCellName() ,
                                               getCellDomainName() ) ;

       KillEvent    killEvent  = new KillEvent( sourceAddr , to ) ;

       String       cellToKill = destination.getCellName() ;

       CellNucleus destNucleus =  _cellList.remove( cellToKill ) ;

       sendToAll( new CellEvent( cellToKill , CellEvent.CELL_DIED_EVENT ) ) ;

       if( destNucleus == null ){
           esay( "Warning : (name not found in _kill) "+cellToKill ) ;
           return ;
       }
       _killedCellList.put( destNucleus.getCellName() , destNucleus ) ;
       destNucleus.sendKillEvent( killEvent ) ;
   }
   private static final int MAX_ROUTE_LEVELS  =  16 ;

   void   sendMessage( CellNucleus nucleus , CellMessage msg )
          throws NotSerializableException,
                 NoRouteToCellException    {

          sendMessage( nucleus , msg , true , true ) ;

   }
   void   sendMessage( CellNucleus nucleus ,
                       CellMessage msg ,
                       boolean     resolveLocally ,
                       boolean     resolveRemotely )
          throws NotSerializableException,
                 NoRouteToCellException    {

      boolean firstSend = ! msg.isStreamMode() ;

      CellMessage transponder = msg ;
      if( firstSend ){
          //
          // this is the original send command
          // - so we have to set the UOID ( sender needs it )
          // - we have to convert the message to stream.
          // - and we have to set our address to find the way back
          //
          transponder = new CellMessage( msg ) ;
          transponder.addSourceAddress( nucleus.getThisAddress() ) ;
      }

      if( transponder.getSourcePath().hops() > 30 ){
         esay( "Hop count exceeds 30, dumping : "+transponder ) ;
         return ;
      }
      CellPath    destination  = transponder.getDestinationPath() ;
      CellAddressCore destCore = destination.getCurrent() ;
      String      cellName     = destCore.getCellName() ;
      String      domainName   = destCore.getCellDomainName();

      say( "sendMessage : "+transponder.getUOID()+" send to "+destination);
      if( _logMessages.isDebugEnabled() ) {

    	  CellMessage messageToSend;

    	  if( transponder.isStreamMode() ) {
    		  messageToSend = new CellMessage(transponder);
    	  }else{
    		  messageToSend = transponder;
    	  }

    	  String messageObject = messageToSend.getMessageObject() == null? "NULL" : messageToSend.getMessageObject().getClass().getName();
    	  _logMessages.debug("glueSendMessage src=" + messageToSend.getSourceAddress() +
  			   " dest=" + messageToSend.getDestinationAddress() + " [" + messageObject + "] UOID=" + messageToSend.getUOID().toString() );
      }
      //
      //  if the cellname is an *, ( stream mode only ) we can skip
      //  this address, because it was needed to reach our domain,
      //  which hopefully happened.
      //
      if( ( ! firstSend ) && cellName.equals("*") ){
            say( "sendMessage : * detected ; skipping destination" );
            destination.next() ;
            destCore = destination.getCurrent() ;
      }


      transponder.isRouted( false ) ;
      //
      // this is the big iteration loop
      //
      for( int iter = 0 ; iter < MAX_ROUTE_LEVELS ; iter ++ ){
         cellName    = destCore.getCellName() ;
         domainName  = destCore.getCellDomainName() ;
         say( "sendMessage : next hop at "+iter+" : "+cellName+"@"+domainName ) ;

         //
         //  now we try to find the destination cell in our domain
         //
         CellNucleus destNucleus = _cellList.get( cellName ) ;
         if( domainName.equals( _cellDomainName ) ){
            if( cellName.equals("*") ){
                  say( "sendMessagex : * detected ; skipping destination" );
                  destination.next() ;
                  destCore = destination.getCurrent() ;
                  continue ;
            }
            //
            // the domain name was specified ( other then 'local' )
            // and points to our domain.
            //
            if( destNucleus == null ){
//               say( "sendMessage : Not found : "+destination ) ;
               if( firstSend ){
                  throw new
                      NoRouteToCellException(
                           transponder.getUOID(),
                           destination,
                           "Initial Send");
               }else{
                  sendException( nucleus , transponder , destination , cellName ) ;
                  return ;
               }
            }
            if( iter == 0 ){
               //
               // here we really found the destination cell ( no router )
               //
//               say( "sendMessage : message "+transponder.getUOID()+
//                    " addToEventQueue of "+cellName ) ;
               destNucleus.addToEventQueue(  new MessageEvent( transponder ) ) ;
            }else{
               //
               // this is a router, so we have to prepare the message for
               // routing
               //
  //             destNucleus.addToEventQueue(  new RoutedMessageEvent( transponder ) ) ;
               transponder.isRouted( true ) ;
               transponder.addSourceAddress(
                    new CellAddressCore( "*" , _cellDomainName ) ) ;
//               say( "sendMessage : message "+transponder.getUOID()+
//                    " forwarded addToEventQueue of "+cellName ) ;
               destNucleus.addToEventQueue(  new RoutedMessageEvent( transponder ) ) ;
            }
            return ;
         }else if( domainName.equals( "local" ) &&
                   ( resolveLocally || ( iter != 0 )  ) ){
            //
            // the domain name was 'local'  AND
            // (  we are assumed to deliver locally ||
            //    we are already in the routing part   )
            //
            if( destNucleus != null ){
//               say( "sendMessage : locally delivered : "+destination ) ;
               if( iter == 0 ){
//                  say( "sendMessage : message "+transponder.getUOID()+
//                       " addToEventQueue of "+cellName ) ;
                  destNucleus.addToEventQueue(  new MessageEvent( transponder ) ) ;
               }else{
                  transponder.isRouted( true ) ;
                  transponder.addSourceAddress(
                       new CellAddressCore( "*" , _cellDomainName ) ) ;
//                  say( "sendMessage : message "+transponder.getUOID()+
//                       " forwarded addToEventQueue of "+cellName ) ;
                  destNucleus.addToEventQueue(  new RoutedMessageEvent( transponder ) ) ;
               }
               return ;
            }else if( iter == MAX_ROUTE_LEVELS ){
               say( "sendMessage : max route iteration reached : "+destination ) ;
               if( firstSend ){
                  throw new
                       NoRouteToCellException(
                          transponder.getUOID(),
                          destination ,
                          "Initial Send");
               }else{
                  sendException( nucleus , transponder , destination ,  cellName ) ;
                  return ;
               }
            }
            //
            // destNuclues == null , is no problem in our case because
            // 'wellknowncells' also use local as keyword.
            //
         }else if( domainName.equals( "local" ) &&
                   ( ! resolveRemotely        ) &&
                   ( iter == 0                )     ){
            //
            // the domain is specified AND
            // we are assumed not to deliver remotely AND
            // we are not yet in the routing part
            //
            throw new
                NoRouteToCellException(
                     transponder.getUOID(),
                     destination,
                     " ! resolve remotely : "+destCore);

         }
         //
         // so, the destination cell wasn't found locally.
         // let's consult the routes
         //
         CellRoute route = _routingTable.find( destCore ) ;
         if( ( route == null ) || ( iter == MAX_ROUTE_LEVELS )){
            say( "sendMessage : no route destination for : "+destCore ) ;
            if( firstSend ){
               throw new
                  NoRouteToCellException(
                     transponder.getUOID(),
                     destination,
                     "Missing routing entry for "+destCore);
            }else{
               sendException( nucleus , transponder , destination , destCore.toString() ) ;
               return ;
            }
         }
         say( "sendMessage : using route : "+route ) ;
         destCore    = route.getTarget() ;
         if( route.getRouteType() == CellRoute.ALIAS )
             destination.replaceCurrent( destCore ) ;
      }
      // end of big iteration loop

   }
   private void sendException( CellNucleus nucleus ,
                               CellMessage msg ,
                               CellPath    destination ,
                               String      routeTarget )
          throws NotSerializableException,
                 NoRouteToCellException    {
            //
            // here we try to inform the last sender that we are
            // not able to deliver the packet.
            //
            say( "sendMessage : Route target Not found : "+routeTarget ) ;
            NoRouteToCellException exception =
                 new   NoRouteToCellException(
                              msg.getUOID() ,
                              destination ,
                              "Tunnel cell >"+routeTarget+
                              "< not found at >"+_cellDomainName+"<" ) ;
            CellPath retAddr = (CellPath)msg.getSourcePath().clone() ;
            retAddr.revert() ;
            CellExceptionMessage ret =
                 new CellExceptionMessage( retAddr , exception )  ;
            esay( "Sending CellException to "+retAddr ) ;
            ret.setLastUOID( msg.getUOID() ) ;
            sendMessage( nucleus , ret ) ;

   }
   void addCellEventListener( CellNucleus nucleus , CellEventListener listener ){
      List<CellEventListener> v = null ;
      if( ( v = _cellEventListener.get( nucleus.getCellName() ) ) == null ){
         _cellEventListener.put( nucleus.getCellName() , v = new Vector<CellEventListener>() ) ;
      }
      v.add( listener ) ;
   }
   public String toString(){ return _cellDomainName ; }

}
