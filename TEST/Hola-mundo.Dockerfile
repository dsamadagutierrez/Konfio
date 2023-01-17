FROM mcr.microsoft.com/dotnet/core/aspnet:3.1

ENV ASPNETCORE_ENVIRONMENT=Development
ENV ASPNETCORE_URLS http://+:80

ENV DOTNET_SYSTEM_GLOBALIZATION_INVARIANT=false

EXPOSE 80

WORKDIR /app
COPY /site .

ENTRYPOINT ["dotnet", "test.hola-mundo.Web.dll"]
