function [state] = FallDetector(CSV)

m = readtable(CSV);
M = table2array(m);

time = zeros(length(M(:,1)),1);

for i=2:length(M(:,1))
    time(i) = M(i,1) + time(i-1);
end

figure
plot(time, M(:,2), time, M(:,3), time, M(:,4));
title('Raw Acceleration Data')
legend('X','Y','Z')

figure
plot(time, M(:,5), time, M(:,6), time, M(:,7));
title('Filtered Acceleration Data')
legend('X','Y','Z')

figure
subplot(1,3,1), plot(time, M(:,8))
legend('sma')
subplot(1,3,2), plot(time, M(:,9));
legend('sva')
subplot(1,3,3), plot(time, M(:,10))
legend('ta')
sgtitle('Variables del algoritmo','FontWeight', 'bold','FontSize',11)

% Media móvil de 3 valores

x_filtered = zeros(length(M(:,1)),1);
y_filtered = zeros(length(M(:,1)),1);
z_filtered = zeros(length(M(:,1)),1);

x_filtered(1) = M(1,2)/3;
y_filtered(1) = M(1,3)/3;
z_filtered(1) = M(1,4)/3;

x_filtered(2) = (M(1,2)+M(2,2))/3;
y_filtered(2) = (M(1,3)+M(2,3))/3;
z_filtered(2) = (M(1,4)+M(2,4))/3;

for i=3:length(M(:,1))
    x_filtered(i) = (M(i-2,2)+M(i-1,2)+M(i,2))/3;
    y_filtered(i) = (M(i-2,3)+M(i-1,3)+M(i,3))/3;
    z_filtered(i) = (M(i-2,4)+M(i-1,4)+M(i,4))/3;
end

figure
plot(time, x_filtered, time, y_filtered, time, z_filtered);
title('Filtered Acceleration Data')
legend('X','Y','Z')

sma = zeros(1,length(M(:,1)));
smv = zeros(1,length(M(:,1)));
ta = zeros(1,length(M(:,1)));

for i=1:length(M(:,1))
   sma(i) = x_filtered(i) + y_filtered(i) + z_filtered(i);
   smv(i) = sqrt(x_filtered(i)^2 + y_filtered(i)^2 + z_filtered(i)^2);
   ta(i) = asind(y_filtered(i)/smv(i));
end


figure
subplot(1,3,1), plot(time, sma)
legend('sma')
subplot(1,3,2), plot(time, smv);
legend('sva')
subplot(1,3,3), plot(time, ta)
legend('ta')
sgtitle('Mis variables del algoritmo','FontWeight', 'bold','FontSize',11)




end