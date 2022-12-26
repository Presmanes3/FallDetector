function state = FallDetector(CSV, N)

m = readtable(CSV);
M = table2array(m);

time = zeros(length(M(:,1)),1);

for i=2:length(M(:,1))
    time(i) = M(i,1) + time(i-1);
end

figure
plot(time, M(:,2), time, M(:,3), time, M(:,4));
title('Datos sin procesar')
legend('X','Y','Z')

figure
plot(time, M(:,5), time, M(:,6), time, M(:,7));
title('Datos filtrados')
legend('X','Y','Z')

max(M(:,8))
max(M(:,9))
max(M(:,10))
min(M(:,8))
min(M(:,9))
min(M(:,10))



figure
plot(time, M(:,8))
legend('sma')
sgtitle('SMA','FontWeight', 'bold','FontSize',11)
figure
plot(time, M(:,9));
legend('svm')
sgtitle('SVM','FontWeight', 'bold','FontSize',11)
figure
plot(time, M(:,10))
legend('ta')
sgtitle('TA','FontWeight', 'bold','FontSize',11)

% Conversión de m/s^2 a g
M(:,2) = M(:,2)./9.80665;
M(:,3) = M(:,3)./9.80665;
M(:,4) = M(:,4)./9.80665;


% Media móvil de N valores

% N=3;

x_filtered = zeros(length(M(:,1)),1);
y_filtered = zeros(length(M(:,1)),1);
z_filtered = zeros(length(M(:,1)),1);

x_filtered(1) = M(1,2)/N;
y_filtered(1) = M(1,3)/N;
z_filtered(1) = M(1,4)/N;

for i=2:N-1
    x_filtered(i) = (M(i,2) + x_filtered(i-1)*N)/N;
    y_filtered(i) = (M(i,3) + y_filtered(i-1)*N)/N;
    z_filtered(i) = (M(i,4) + z_filtered(i-1)*N)/N;
end

for i=N:length(M(:,1))
    x_filtered(i) = 0;
    y_filtered(i) = 0;
    z_filtered(i) = 0;
    for j=0:N-1
        x_filtered(i) = x_filtered(i) + M(i-j,2);
        y_filtered(i) = y_filtered(i) + M(i-j,3);
        z_filtered(i) = z_filtered(i) + M(i-j,4);
    end
    x_filtered(i) = x_filtered(i)/N;
    y_filtered(i) = y_filtered(i)/N;
    z_filtered(i) = z_filtered(i)/N;
end

figure
plot(time, x_filtered, time, y_filtered, time, z_filtered);
title(sprintf('Datos Aceleración Filtrados para N = %d', N))
legend('X','Y','Z')

sma = zeros(1,length(M(:,1)));
svm = zeros(1,length(M(:,1)));
ta = zeros(1,length(M(:,1)));

for i=1:length(M(:,1))

    total_time=0;
   
   if i<=20;
       k=i-1;
   else 
       k=20;
   end

   for j=i-k:i
        total_time = total_time + M(j,1);
   end

   x_sma = 0;
   y_sma = 0;
   z_sma = 0;
   for j=i-k:i
        x_sma = x_sma + abs(M(j,1)*x_filtered(j));
        y_sma = y_sma + abs(M(j,1)*y_filtered(j));
        z_sma = z_sma + abs(M(j,1)*z_filtered(j));
   end

   sma(i) = (x_sma + y_sma + z_sma)/total_time;
   svm(i) = sqrt(x_filtered(i)^2 + y_filtered(i)^2 + z_filtered(i)^2);
   ta(i) = asind(y_filtered(i)/svm(i));

end

figure
subplot(1,3,1), plot(time, sma)
yline(2,'LineStyle','-.','Color','red','LineWidth',3)
legend('sma')
<<<<<<< HEAD
subplot(1,3,2), plot(time, svm);
yline(4,'LineStyle','-.','Color','red','LineWidth',3)
=======
subplot(1,3,2), plot(time, smv);
>>>>>>> 5f7697a81f27a0f059824bbe39c1c6dc49da8614
legend('svm')
subplot(1,3,3), plot(time, ta)
yline(40,'LineStyle','-.','Color','red','LineWidth',3)
legend('ta')
sgtitle(sprintf('Variables del algoritmo para N = %d', N),'FontWeight', 'bold','FontSize',11)

j = 1;
state(j) = "null";

for i=1:length(sma)
    if sma(i) >= 2
        if svm(i) >=4
            if ta(i) < 40
                cur_state = "caida";
            else
                cur_state = "subiendo / levatandose";
            end
        else
            cur_state = "caminando";
        end
    elseif ta(i) >= 40
        cur_state = "sentado / de pie";
    else
        cur_state = "tumbado";
    end
    if cur_state ~= state(j) 
        state(j) = cur_state;
        j = j+1;
        state(j) = cur_state;
    end
end

state = state(1:end-1);
