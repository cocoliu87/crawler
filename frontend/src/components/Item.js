import React from "react";
import { Card, CardContent, Typography, Link } from "@mui/material";

export default function Item({
  id,
  index,
  url,
  text,
  pagerank,
  score,
  cosineSimiliarty,
  history,
  setHistory,
}) {
  const clickHandler = (url) => {
    let historyCopy = [...history];
    const exist = historyCopy.find((one) => one.url === url);
    if (exist) {
      return;
    } else {
      historyCopy.unshift({ name: "cached page", url: url });
      historyCopy = historyCopy.slice(0, 5);
      setHistory(historyCopy);
    }
  };

  return (
    <div>
      <Card>
        <CardContent>
          <Typography variant="h5" component="div">
            <Link href={`${url}`} target="_blank" onClick={() => clickHandler(url)}>
              {url}
            </Link>
          </Typography>
          <Typography variant="body2">{text}</Typography>
          <p>
            PageRank: {pagerank}, CosineSimiliarty: {cosineSimiliarty}, Score: {score}
          </p>
        </CardContent>
      </Card>
    </div>
  );
}
