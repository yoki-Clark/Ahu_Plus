FROM python:3.11-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1

WORKDIR /app
COPY server/market_index/requirements.txt /app/server/market_index/requirements.txt
RUN pip install --no-cache-dir -r /app/server/market_index/requirements.txt
COPY server /app/server

CMD ["uvicorn", "server.market_index.main:app", "--host", "0.0.0.0", "--port", "8000"]
