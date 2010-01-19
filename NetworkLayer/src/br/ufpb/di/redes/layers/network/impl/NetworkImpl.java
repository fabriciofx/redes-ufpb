package br.ufpb.di.redes.layers.network.impl;


import br.ufpb.di.redes.layers.all.InterlayerData;
import br.ufpb.di.redes.layers.datalink.interfaces.DataLink;
import br.ufpb.di.redes.layers.network.impl.tables.Table;
import br.ufpb.di.redes.layers.network.interfaces.Network;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * Implementacao da camada de rede. Essa classe extende a classe abstrata Network.
 *
 * @author Joedson Marques - joedson7[at]hotmail.com
 * @author Elenilson Vieira - elenilson[at]elenilsonvieira.com
 *
 * @since 15 de janeiro de 2009
 */
public class NetworkImpl extends Network {
    
    private int [] source_ips;
    public final int SMALLER_DATALINK_MAX_PACKET_SIZE;



    /**
     * PATERN_IP_POSITION - Usado para quando nenhum dos meus IPs concincidir com o IP recebido de cima
     */
    public enum Constants {NETWORK_LENGHT_OF_IP(2), STATION_LENGHT_OF_IP(2), 
        PATERN_IP_POSITION(0), SEQUENCY_NUMBER(2), TTL(2),
        HEADER_LENGHT(NETWORK_LENGHT_OF_IP.value + STATION_LENGHT_OF_IP.value + SEQUENCY_NUMBER.value + TTL.value),
        NETWORK_FULL_ADDRESS_SIZE(NETWORK_LENGHT_OF_IP.value + STATION_LENGHT_OF_IP.value);
        private int value;

        private Constants(int value){
            this.value = value;
        }

        public int getValue(){
            return value;
        }
    }
    
     /**
     * Chave - Rede ou Estacao
     * Valor - Ip de quem envia para essa rede ou estacao
     */
    private Map <Integer,Integer> route_table= new HashMap<Integer,Integer>();

    /**
     * Chave - Ip
     * Valor - Mac correspondente a esse ip
     */
    private Table arp_table = new Table();
    
    /**
     * Construtor da classe
     *
     * Formato do cabecalho:
     * 0 ao 3 bit source_ip (bits mais significativos a direita)
     * do 4 ao 7 bit destination_ip (bits mais significativos a direita)
     * 
     * @param downLayers as camadas inferiores
     * @param source_ips os ips origem que cada posicao DEVE esta associoada a cada posicao do array de enlaces
     */
    public NetworkImpl(DataLink[] downLayers, int [] source_ips, int smaller_datalink_max_packet_size) {
        super(downLayers);
        this.SMALLER_DATALINK_MAX_PACKET_SIZE = smaller_datalink_max_packet_size;
        this.source_ips = source_ips;
    }

    @Override
    protected void processReceivedData(InterlayerData data, int soruce_mac, int datalink_id) {
        int ip_dest = data.takeInfo(Constants.NETWORK_FULL_ADDRESS_SIZE.getValue(), Constants.NETWORK_FULL_ADDRESS_SIZE.getValue());
        
        //Pega o IP destino e verifica se e' o nosso IP, se o pacote nao for para nos repessa o pacote para o enlace
        if(!containsIp(ip_dest)){
            int dest_mac = getMacToSendToIp(ip_dest, datalink_id);
            bubbleDown(data, dest_mac, datalink_id);
            return;
        }
        
        //Se for para nos, cria um novo pacate para manda para camada de cima
        InterlayerData dataToTransport = new InterlayerData(data.length - Constants.HEADER_LENGHT.getValue());

        //Obs: Total de bits que ele vai ler: "data.length - HEADER_LENGHT', se for menor q zero ferrou
        //Copia de bits
        InterlayerData.copyBits(dataToTransport, data, (int) Constants.HEADER_LENGHT.getValue(),
                data.length - Constants.HEADER_LENGHT.getValue(), 0);

        //Obtem o ip
        int source_ip = data.takeInfo(0, Constants.NETWORK_FULL_ADDRESS_SIZE.getValue());
        bubbleUp(dataToTransport, source_ip);//manda para cima
    }

    //Recebe de tansporte e devo passar pra enlace
    //Algoritmo:
    //

    @Override
    protected void processSentData(InterlayerData data, int dest_ip) {
        //Array de bits suficiente para anexar o nosso cebecalho
        InterlayerData dataToDataLink = new InterlayerData(data.length + Constants.HEADER_LENGHT.getValue());

        //Coloca o endereco destino no arrays de bits que sera enviado para o enlace
        int source_ip = getIp(dest_ip);
        
        //Obs: Erro, tem q procurar para onde ENVIAR se o Ip não for minha rede por exemplo

        //Adiciona os respectivos IPs origem e destino
        dataToDataLink.putInfo(0, Constants.NETWORK_FULL_ADDRESS_SIZE.getValue(), source_ip);
        dataToDataLink.putInfo(Constants.NETWORK_FULL_ADDRESS_SIZE.getValue(), Constants.NETWORK_FULL_ADDRESS_SIZE.getValue(), dest_ip);

        //Copia dos bits
        InterlayerData.copyBits(dataToDataLink, data, 0, data.length, Constants.HEADER_LENGHT.getValue());
        int datalink_id=getIdDatalinkFromIp(source_ip);
        int dest_mac=getMacToSendToIp(dest_ip, datalink_id);//Obtem o mac para onde enviar o determinado IP
        //Msn ideia mas, getMacFromIp
        bubbleDown(dataToDataLink,dest_mac,datalink_id);
    }

    @Override
    public int maxPacketSize() {
        /*
         * So posso quebrar o pacote recebido no
         * numero maximo que posso representar no numero de sequencia 'x' o menor maximo dos enlaces.
         */
        return SMALLER_DATALINK_MAX_PACKET_SIZE * Constants.SEQUENCY_NUMBER.value;
    }

    @Override
    public int minPacketSize() {
        return Constants.HEADER_LENGHT.value;
    }

    @Override
    public int getIp() {
        return source_ips[Constants.PATERN_IP_POSITION.getValue()];
    }

    /**
     * Procura o id do enlace dado o ip origem
     *
     * @param source_ip o ip origem
     *
     * @return o id do enlace relacionado com o ip origem
     */
    private int getIdDataLink(int source_ip) {
        for(int posicao = 0; posicao < source_ips.length; posicao++)
            if(source_ips[posicao] == source_ip)
                return posicao;

        return -1;
    }

    /**
     * Procura no array de ips o ip correspondente ao id do enlace.
     *
     * @param id_dataLink o id do enlace
     *
     * @return o ip correspondente ao id do enlace
     */
    private int getIpFromDataLinkId(int id_dataLink){
        return source_ips[id_dataLink];
    }

    /**
     * Verifica se tem algum ip origem na mesma rede do ip destino,
     * caso contrario uso retorno o ip padrao
     *
     * @param dest_ip o ip destino
     *
     * @return o ip origem relacionado com o ip destino
     */
    private int getIp(int dest_ip) {
        
        InterlayerData network_dest_ip = new InterlayerData(Constants.NETWORK_LENGHT_OF_IP.getValue());
        network_dest_ip.putInfo(0, Constants.NETWORK_LENGHT_OF_IP.getValue(), dest_ip);
        int dest_network = network_dest_ip.takeInfo(0, Constants.NETWORK_LENGHT_OF_IP.getValue());

        InterlayerData network_source_ip = new InterlayerData(Constants.NETWORK_FULL_ADDRESS_SIZE.getValue());

        for(int ip: source_ips){

            network_source_ip.putInfo(0, Constants.NETWORK_LENGHT_OF_IP.getValue(), ip);
            int source_network = network_source_ip.takeInfo(0, Constants.NETWORK_LENGHT_OF_IP.getValue());
            if(source_network == dest_network)
                return ip;
        }

        return source_ips[Constants.PATERN_IP_POSITION.getValue()];
    }

    /** 
     * Define se o ip passado como parametro esta ou nao no array de ips
     *
     * @param ip o ip a ser procurado
     *
     * @return um boolean indicando se o ip esta contido.
     */
    private boolean containsIp(int ip) {

        for(int pos : source_ips){
            if(pos == ip)
                return true;
        }

        return false;
    }
    
    /**
     * Retorna o mac que deve ser usado para enviar o pacote para o ip passado 
     * como argumento.
     * 
     * @param ip_dest o ip
     * 
     * @return o mac a ser usado para enviar o pacote ou -1 se nada foi encontrado
     */
    private int getMacToSendToIp(int ip_dest, int id_dataLink) {
        int network_dest = splitIP(ip_dest)[0];

        int my_ip = getIpFromDataLinkId(id_dataLink);
        int my_network = splitIP(my_ip)[0];

        //int map_size = route_table.sizeMap(my_ip);
        //Comecado a modificacao
        int map_size =route_table.size();

        //O maximo de loops que eu darei sera o tamanho da tabela. Evita loop infinito!
        for(int i = 0; i < map_size; i++){
            //int ip_sender = route_table.get(my_ip, network_dest);
            int ip_sender = getInRouteTable(network_dest);
            int network_sender = splitIP(ip_sender)[0];

            if(network_sender == my_network||ip_sender==my_ip)
                return arp_table.get(my_ip, ip_sender);//Retorna o MAC e ip_sender(quem envia para ip_dest)

            network_dest = splitIP(ip_sender)[0];
        }

        return -1;
    }

    /**
     * Retira o ip do InterlayerData e retorna o endereco de rede e da estacao.
     *
     * @param interlayerData o pacote
     *
     * @return um array onde a primeira posicao indica o endereco de rede e a segunda indica o endereco da estacao
     */
    private int[] splitIP(InterlayerData interlayerData){
        int network = interlayerData.takeInfo(0, Constants.NETWORK_LENGHT_OF_IP.getValue());
        int station = interlayerData.takeInfo(Constants.NETWORK_LENGHT_OF_IP.getValue(), Constants.STATION_LENGHT_OF_IP.getValue());

        return new int[]{network, station};
    }

    /**
     * Retira do ip o endereco de rede e da estacao.
     *
     * @param ip o ip
     *
     * @return um array onde a primeira posicao indica o endereco de rede e a segunda indica o endereco da estacao
     */
    private int[] splitIP(int ip){
        InterlayerData interlayerData = new InterlayerData(Constants.NETWORK_FULL_ADDRESS_SIZE.getValue());

        interlayerData.putInfo(0, Constants.NETWORK_FULL_ADDRESS_SIZE.getValue(), ip);

        return splitIP(interlayerData);
    }

    /**
     * Seta os valores em uma das tabelas.
     *
     * @param my_ip o ip correspondente a tabela. Se nao existir tabela para esse ip, uma nova sera criada.
     * @param key a chave na tabela correspondente ao ip passado como argumento
     * @param value o valor correspondente 'a chave na tabela correspondente ao ip passado como argumento
     */
    public void setInTable(int my_ip, int key, int value){

         arp_table.set(my_ip, key, value);
            //case ROUTE_TABLE: route_table.set(my_ip, key, value); break;
      
    }

    /**
     * Remove o par chave/valor da tabela do ip passado como argumento.
     *
     * @param my_ip o ip correspondente a tabela.
     * @param key a chave na tabela correspondente ao ip passado como argumento
     *
     * @return o valor removido ou -1 se nao existia
     */
    public int removeInTable(int my_ip, int key){

        int value = -1;

        value = arp_table.remove(my_ip, key);
        //case ROUTE_TABLE: value = route_table.remove(my_ip, key); break;
      
        return value;
    }

    /**
     * Retorna o valor da tabela do ip passado como argumento.
     *
     * @param my_ip o ip correspondente a tabela.
     * @param key a chave na tabela correspondente ao ip passado como argumento
     *
     * @return o valor ou -1 se nao existia
     */
    public int getInTable( int my_ip, int key){
        int value = -1;


        value = arp_table.get(my_ip, key);
            //case ROUTE_TABLE: value = route_table.get(my_ip, key); break;

        return value;
    }
    /**
     * Retorna o ip (sender) que envia para o ip passado como parâmetro.
     *
     * @param to_send_ip ip a cujo enviador será buscado na tabela
     *
     * @return o ip caso o mesmo sejá encontrado ou -1 caso contrário
     */
    public int getInRouteTable(int to_send_ip){

        //Se encontramos a chave retornamos a mesma caso contrario returnamos -1
        if(route_table.containsKey(to_send_ip))
            return route_table.get(to_send_ip);//Retorna que envia para o determinado IP

        return -1;
    }

    /**
     * Altera a rota passada como parametro ou adiciona, caso a rota nao existe
     *
     * @param to_send_ip no caso o meu ip
     * @param dest_ip ip responsavel por enviar ao ip to_se
     *
     */
    public void setInRouteTable(int to_send_ip, int dest_ip){
        route_table.put(to_send_ip, dest_ip);
    }

    /**
     *
     * Obtem o identificador do enlace dado o ip
     *
     * @param source_ip
     *
     * @return o identificador do enlace
     */
    private int getIdDatalinkFromIp(int source_ip) {
        for(int i=0;i<source_ips.length;i++){

            if(source_ips[i]==source_ip)
                return i;

        }

        return Constants.PATERN_IP_POSITION.getValue();
    }

}